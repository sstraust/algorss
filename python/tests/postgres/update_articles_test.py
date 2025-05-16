import pytest
import psycopg
import json

from postgres import update_articles
from postgres.update_articles import copy_update_to_db, remove_old_articles
from postgres import embeddings
import postgres.tables
import testing.postgresql
import numpy as np
from pgvector.psycopg import register_vector
from datetime import datetime, timedelta
import pytz
from tests.postgres.util import db_conn
import mongo_tables
from bson import ObjectId

TEST_SOURCE_ID = "TEST_SOURCE_ID"    


@pytest.fixture
def mock_find_one(monkeypatch):
    def _mock_find_one(*args, **kwargs):
        return {
            "_id": ObjectId(),
            "link": kwargs.get("link", "default_link"),
            "source_id": kwargs.get("source_id", "default_source")
        }

    monkeypatch.setattr(mongo_tables.NEWS_ARTICLES, "find_one", _mock_find_one)




def test_copy_update_to_db__inserts_article(monkeypatch, db_conn, mock_find_one):
    def fake_huggingface_query(input):
        return np.random.rand(384)
    monkeypatch.setattr(embeddings, 'query_huggingface', fake_huggingface_query)

    article_data = {
        "source_id": "test_id",
        "link": "test_link",
        "description": "test_description",
        "title": "test_title",
        "published_date": datetime.utcnow()
        
    }
        
    

    copy_update_to_db(
        db_conn,
        article_data["source_id"],
        article_data["link"],
        article_data["published_date"],
        article_data
    )

    cur = db_conn.cursor()
    cur.execute(
        """
        SELECT source_id, link, title_embedding,
        description_embedding, news_data
        FROM articles
        """
    )
    rows = cur.fetchall()
    assert len(rows) == 1, f"Expected exactly one row, got {len(rows)}"
    only_row = rows[0]

    assert only_row[0] == "test_id"
    assert only_row[1] == "test_link"
    assert isinstance(only_row[2], np.ndarray)
    assert len(only_row[2]) == 384
    assert isinstance(only_row[3], np.ndarray)
    assert len(only_row[3]) == 384
    only_row[4]["published_date"] = datetime.fromisoformat(only_row[4]["published_date"])
    assert only_row[4] == article_data 


def remove_old_articles_insert_rows(cur, rows):
    """
    rows: list of tuples (link, source_id, published_date)
    """
    cur.executemany(
        "INSERT INTO articles (link, source_id, published_date, mongo_id) VALUES (%s, %s, %s, %s)",
        rows
    )


def remove_old_articles_count_rows(cur):
    cur.execute("SELECT COUNT(*) FROM articles WHERE source_id = %s", (TEST_SOURCE_ID,))
    return cur.fetchone()[0]


def remove_old_articles_fetch_dates(cur):
    cur.execute(
        "SELECT published_date FROM articles WHERE source_id = %s ORDER BY published_date NULLS FIRST, published_date ASC",
        (TEST_SOURCE_ID,)
    )
    return [row[0] for row in cur.fetchall()]

# Tests for remove_old_articles

def test_no_deletion_if_under_limit(db_conn, mock_find_one):
    cur = db_conn.cursor()
    now = datetime.utcnow()
    rows = [
        (f"link{i}", TEST_SOURCE_ID, now - timedelta(days=i), str(i) ) 
        for i in range(10)
    ]
    remove_old_articles_insert_rows(cur, rows)
    db_conn.commit()

    remove_old_articles(cur, TEST_SOURCE_ID, limit=50)
    assert remove_old_articles_count_rows(cur) == 10


def test_delete_nulls_first(db_conn, mock_find_one):
    cur = db_conn.cursor()
    now = datetime.utcnow()
    rows_null = [(f"linknull{i}", TEST_SOURCE_ID, None, str(i)) for i in range(55)]
    remove_old_articles_insert_rows(cur, rows_null)
    rows_normal = [(f"link{i}", TEST_SOURCE_ID, now - timedelta(days=i), str(i)) for i in range(55)]
    remove_old_articles_insert_rows(cur, rows_normal)
    db_conn.commit()

    remove_old_articles(cur, TEST_SOURCE_ID, limit=50)
    remaining = remove_old_articles_count_rows(cur)
    assert remaining == 50
    dates = remove_old_articles_fetch_dates(cur)
    assert all(d is not None for d in dates)


def test_delete_oldest_after_nulls(db_conn, mock_find_one):
    cur = db_conn.cursor()
    now = datetime.now(pytz.timezone("America/New_York"))
    valid_rows = [
        (f"link{i}", TEST_SOURCE_ID, now - timedelta(days=60 - i), str(i))
        for i in range(60)
    ]
    remove_old_articles_insert_rows(cur, valid_rows)
    rows_null = [(f"linknull{i}", TEST_SOURCE_ID, None, str(i)) for i in range(55)]
    remove_old_articles_insert_rows(cur, rows_null)
    
    db_conn.commit()

    remove_old_articles(cur, TEST_SOURCE_ID, limit=50)
    assert remove_old_articles_count_rows(cur) == 50

    dates = remove_old_articles_fetch_dates(cur)
    expected_min = (now - timedelta(days=50)).replace(microsecond=0)
    actual_min = min(dates).replace(microsecond=0)
    assert actual_min == expected_min


def test_mixed_null_and_valid(db_conn, mock_find_one):
    cur = db_conn.cursor()
    now = datetime.utcnow()
    rows = [(f"nlink{i}", TEST_SOURCE_ID, None, str(i)) for i in range(30)]
    rows += [(f"vlink{i}", TEST_SOURCE_ID, now - timedelta(days=i), str(i)) for i in range(40)]
    remove_old_articles_insert_rows(cur, rows)
    db_conn.commit()

    remove_old_articles(cur, TEST_SOURCE_ID, limit=50)
    assert remove_old_articles_count_rows(cur) == 50
    dates = remove_old_articles_fetch_dates(cur)
    assert len([x for x in dates if x is not None]) == 40
    assert len(dates) == 50
    
