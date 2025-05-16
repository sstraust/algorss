import psycopg
from psycopg.types.json import Json
from psycopg import sql
from postgres import tables
from postgres import embeddings
from typing import Optional, Dict, Any, Union
import numpy as np
from pgvector.psycopg import register_vector
from datetime import datetime
import pymongo
import mongo_tables


CONN = tables.connect_to_db()
register_vector(CONN)


def clean_for_json(data):
    def convert(value):
        if isinstance(value, datetime):
            return value.isoformat()
        elif isinstance(value, dict):
            return {k: convert(v) for k, v in value.items()}
        elif isinstance(value, list):
            return [convert(v) for v in value]
        else:
            return value
    return convert(data)


def remove_old_articles(cur, source_id: str, limit: int = 50) -> None:
    # 1. Count how many articles for this source
    cur.execute(
        "SELECT COUNT(*) FROM articles WHERE source_id = %s",
        (source_id,)
    )
    count = cur.fetchone()[0]
    
    # 2. If over limit, delete the excess in one go
    excess = count - limit
    if excess > 0:
        # Use a CTE to pick the `excess` rows: NULL dates first, then oldest timestamps
        cur.execute(
        """
        WITH to_delete AS (
        SELECT ctid
        FROM articles
        WHERE source_id = %s
        ORDER BY
        (published_date IS NOT NULL) ASC,  -- NULLs sort first
        published_date ASC                 -- then oldest timestamps
        LIMIT %s
        )
        DELETE FROM articles
        USING to_delete
        WHERE articles.ctid = to_delete.ctid
        """,
            (source_id, excess)
        )

def copy_update_to_db(conn, source_id: str, link: str, published_date, article_data: Dict):
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT 1 FROM articles WHERE source_id = %s AND LINK = %s", (source_id, link,))
            exists = cur.fetchone()

            title_embedding = None
            description_embedding = None
            if not exists:
                if (not article_data["title"]) or (not article_data["description"]):
                    print("missing title or description")
                title_embedding = embeddings.query_huggingface(article_data["title"])
                description_embedding = embeddings.query_huggingface(article_data["description"])


            mongo_article = mongo_tables.NEWS_ARTICLES.find_one({"link": link, "source_id": source_id})
            mongo_id_str = str(mongo_article["_id"])

            cur.execute(
                """
                INSERT INTO articles
                (link, source_id, published_date, title_embedding, description_embedding, news_data, mongo_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (link, source_id)
                DO UPDATE SET
                news_data = EXCLUDED.news_data
                """,
                (
                    link,
                    source_id,
                    published_date,
                    title_embedding,
                    description_embedding,
                    Json(clean_for_json(article_data)),
                    mongo_id_str
                )
            )
            remove_old_articles(cur, source_id)

    except Exception as e:
        print(e)
        print("failed to insert or update article: " + link)
        
    finally:
        conn.commit()            
