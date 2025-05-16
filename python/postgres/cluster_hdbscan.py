import psycopg
import numpy as np
import hdbscan
from postgres.tables import connect_to_db
import postgres.tables
from collections import defaultdict
import sys
import postgres.update_articles
import time


def get_all_articles(conn):
    cur = conn.cursor()
    cur.execute("""
    SELECT id, news_data->'title' AS title, title_embedding
    FROM articles
    WHERE title_embedding IS NOT NULL
    """)
    rows = cur.fetchall()
    cur.close()
    ids      = [row[0] for row in rows]
    titles   = [row[1] for row in rows]
    title_embeddings = [row[2] for row in rows]
    # title_embeddings = np.vstack([
    #     np.fromstring(e.strip('[]'), sep=',', dtype=np.float32)
    #     for e in title_embeddings_raw
    # ])

    return {
        "ids": ids,
        "titles": titles,
        "title_embeddings": title_embeddings
    }
        

def calculate_clusters(conn):
    article_data = get_all_articles(conn)
    clusterer = hdbscan.HDBSCAN(
        min_cluster_size=4,
        metric='euclidean',
        cluster_selection_method='eom'
    )
    labels = clusterer.fit_predict(article_data["title_embeddings"])
    max_label_id = max(labels) + 1
    for i in range(len(labels)):
        if labels[i] == -1:
            labels[i] = max_label_id
            max_label_id = max_label_id + 1
        

    clusters = defaultdict(list)
    cluster_embeddings = defaultdict(list)

    max_cluster_id = max(labels) + 1
    
    for lbl, id, emb in zip(labels, article_data["ids"], article_data["title_embeddings"]):
        clusters[lbl].append(id)
        cluster_embeddings[lbl].append(emb)

    cluster_avg_embeddings = {}
    for cluster_id, _ in cluster_embeddings.items():
        cluster_avg_embeddings[cluster_id] = np.mean(cluster_embeddings[cluster_id], axis=0)

    cluster_data = {}
    for cluster_id, _ in clusters.items():
        cluster_data[cluster_id] = {
            "article_ids": clusters[cluster_id],
            "avg_embedding": cluster_avg_embeddings[cluster_id]
        }

    return cluster_data


def write_clusters_to_db(conn):
    postgres.tables.create_recent_clusters_table(conn)


    cluster_data = calculate_clusters(conn)

    cur = conn.cursor()
    cur.execute("""
    TRUNCATE TABLE recent_clusters;
    """)

    for cluster_id, cluster_info in cluster_data.items():
        if cluster_id != -1:
            cur.execute(
                """
                INSERT INTO recent_clusters
                (avg_embedding, article_ids)
                VALUES (%s, %s)
                """,
                (
                    cluster_info["avg_embedding"],
                    cluster_info["article_ids"]
                )
            )

    conn.commit()
                        
            
            
if __name__ == "__main__":
    while True:
        write_clusters_to_db(postgres.update_articles.CONN)
        print("done")
        time.sleep(60*60)
