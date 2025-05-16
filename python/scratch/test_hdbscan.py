import psycopg2
import numpy as np
import hdbscan

# 1) Connect and fetch titles + embeddings
conn = psycopg2.connect(
    dbname="newsv2",
    user="myuser",
    password="mypass",
    host="localhost",
    port=5432
)
cur = conn.cursor()
cur.execute("""
    SELECT id, news_data->'title' AS title, title_embedding
    FROM articles
    WHERE title_embedding IS NOT NULL
""")
rows = cur.fetchall()
cur.close()
conn.close()

# 2) Separate IDs, titles, embeddings
ids      = [row[0] for row in rows]
titles   = [row[1] for row in rows]
embeds_raw = [row[2] for row in rows]


# Convert raw embeddings into a numpy array of shape (n_samples, 384)
# If embeds_raw elements are bytes, adjust this accordingly
embeddings = np.vstack([
    np.fromstring(e.strip('[]'), sep=',', dtype=np.float32)
    for e in embeds_raw
])



# 3) Run HDBSCAN
clusterer = hdbscan.HDBSCAN(
    min_cluster_size=4,
    metric='euclidean',
    cluster_selection_method='eom'
)
labels = clusterer.fit_predict(embeddings)

# 4) Group titles by cluster
from collections import defaultdict
clusters = defaultdict(list)
for lbl, id in zip(labels, ids):
    clusters[lbl].append(id)

# 5) Print out the clusters
for cluster_id, member_ids in sorted(clusters.items()):
    header = "Noise (–1)" if cluster_id == -1 else f"Cluster {cluster_id}"
    if cluster_id == -1:
        continue
    print(f"\n=== {header} ===")
    for t in member_ids:
        print(f"- {t}")
