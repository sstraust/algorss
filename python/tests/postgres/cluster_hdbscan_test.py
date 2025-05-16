

from tests.postgres.util import db_conn
import numpy as np
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock
from postgres import cluster_hdbscan
import postgres.tables

def insert_rows(cur, rows):
    cur.executemany(
        """
        INSERT INTO articles (id, title_embedding, link, source_id, published_date, mongo_id)
        VALUES (%s, %s, %s, %s, %s, %s)
        """,
        rows)

def read_recent_clusters(cur):    
    cur.execute(
            """
            SELECT avg_embedding, article_ids FROM
            recent_clusters
            """)
    return [{"avg_embedding": row[0],
             "article_ids": row[1]}
            for row in cur.fetchall()]

def test_creates_clusters_table(db_conn):
    cur = db_conn.cursor()
    example_articles = [
        [i, np.ones(384),
         f"test{i}", "test", datetime.utcnow(), str(i)]
         for i in range(10)
    ]
    insert_rows(cur, example_articles)
    db_conn.commit()

    with patch("hdbscan.HDBSCAN") as mock_hdbscan_class:
        mock_instance = MagicMock()
        mock_instance.fit_predict.return_value = np.array(
            [1, 2, 3, 2, 1, 1, 1, 3, 4, 3])
        mock_hdbscan_class.return_value = mock_instance

        cluster_hdbscan.write_clusters_to_db(db_conn)

        recent_clusters = read_recent_clusters(db_conn.cursor())
        recent_clusters = sorted(recent_clusters, key=lambda x: min(x["article_ids"]))

        assert recent_clusters[0]["article_ids"] == [0, 4, 5, 6]
        assert recent_clusters[1]["article_ids"] == [1, 3]
        assert recent_clusters[2]["article_ids"] == [2, 7, 9]
        assert recent_clusters[3]["article_ids"] == [8]


def test_calculates_avg_embedding(db_conn):
    cur = db_conn.cursor()
    example_articles = [
        [0, np.ones(384)],
        [1, np.ones(384)*3],
        [2, np.ones(384)*0.5],
        [3, np.ones(384)*0.3],
        [4, np.ones(384)*0.6]]
    example_articles_full =[
        [x[0], x[1], f"test{x[0]}", "test", datetime.utcnow(), str(x[0])]
        for x in example_articles]
    insert_rows(cur, example_articles_full)

    with patch("hdbscan.HDBSCAN") as mock_hdbscan_class:
        mock_instance = MagicMock()
        mock_instance.fit_predict.return_value = np.array(
            [1, 2, 1, 1, 2])
        mock_hdbscan_class.return_value = mock_instance


        cluster_hdbscan.write_clusters_to_db(db_conn)
        
        recent_clusters = read_recent_clusters(db_conn.cursor())
        recent_clusters = sorted(recent_clusters, key=lambda x: min(x["article_ids"]))

        assert np.allclose(recent_clusters[0]["avg_embedding"],  np.ones(384)*((1 + 0.3 + 0.5)/3))
        
        
def test_handles_minus1_clusters(db_conn):
    cur = db_conn.cursor()
    example_articles = [
        [i, np.ones(384),
         f"test{i}", "test", datetime.utcnow(), str(i)]
        for i in range(10)
    ]
    example_articles[2][1] = np.ones(384)*0.5
    insert_rows(cur, example_articles)
    db_conn.commit()

    with patch("hdbscan.HDBSCAN") as mock_hdbscan_class:
        mock_instance = MagicMock()
        mock_instance.fit_predict.return_value = np.array(
            [1, 2, -1, 3, 1, -1, 1, 3, -1, 3])
        mock_hdbscan_class.return_value = mock_instance

        cluster_hdbscan.write_clusters_to_db(db_conn)
        
        recent_clusters = read_recent_clusters(db_conn.cursor())
        recent_clusters = sorted(recent_clusters, key=lambda x: min(x["article_ids"]))


        assert recent_clusters[0]["article_ids"] == [0, 4, 6]
        assert recent_clusters[1]["article_ids"] == [1]
        assert recent_clusters[2]["article_ids"] == [2]
        assert recent_clusters[3]["article_ids"] == [3, 7, 9]
        assert recent_clusters[4]["article_ids"] == [5]
        assert recent_clusters[5]["article_ids"] == [8]

        assert np.allclose(recent_clusters[2]["avg_embedding"], np.ones(384)*0.5)

        


    

def test_handles_missing_or_incomplete_values(db_conn):
    cur = db_conn.cursor()
    example_articles = [
        [i, np.ones(384),
         f"test{i}", "test", datetime.utcnow(), str(i)]
        for i in range(5)
    ]
    example_articles[2][1] = None
    insert_rows(cur, example_articles)
    db_conn.commit()

    with patch("hdbscan.HDBSCAN") as mock_hdbscan_class:
        mock_instance = MagicMock()
        mock_instance.fit_predict.return_value = np.array(
            [1, 2, 1, 1])
        mock_hdbscan_class.return_value = mock_instance

        cluster_hdbscan.write_clusters_to_db(db_conn)
        
        recent_clusters = read_recent_clusters(db_conn.cursor())
        recent_clusters = sorted(recent_clusters, key=lambda x: min(x["article_ids"]))

        assert recent_clusters[0]["article_ids"] == [0, 3, 4]
        assert recent_clusters[1]["article_ids"] == [1]
