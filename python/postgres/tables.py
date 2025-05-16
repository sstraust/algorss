import psycopg
from psycopg import sql

DB_NAME = "newsv2"



def create_articles_table(conn):
    cur = conn.cursor()

    cur.execute("""
    CREATE EXTENSION IF NOT EXISTS vector;
    """);

    cur.execute("""
    CREATE TABLE IF NOT EXISTS articles (
    id SERIAL PRIMARY KEY,
    link TEXT NOT NULL,
    source_id TEXT NOT NULL,
    title_embedding vector(384),
    description_embedding vector(384),
    published_date TIMESTAMPTZ,
    news_data JSONB,
    mongo_id TEXT NOT NULL,
    UNIQUE (link, source_id)
    )
    """)


    cur.execute("""
    CREATE INDEX IF NOT EXISTS title_embedding_hnsw_cosine_idx
    ON articles USING hnsw (title_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
    """)

    cur.execute("""
    CREATE INDEX IF NOT EXISTS description_embedding_hnsw_cosine_idx
    ON articles USING hnsw (description_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
    """)

    
    conn.commit()

    
def create_recent_clusters_table(conn):
    cur = conn.cursor()
    
    cur.execute("""
    CREATE EXTENSION IF NOT EXISTS vector;
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS recent_clusters (
    id SERIAL PRIMARY KEY,
    avg_embedding vector(384),
    article_ids INTEGER[]
    )
    """)

    cur.execute("""
    CREATE INDEX IF NOT EXISTS idx_article_ids ON recent_clusters USING GIN (article_ids);
    """)

    cur.execute("""
    CREATE INDEX IF NOT EXISTS avg_embedding_hnsw_cosine_idx
    ON recent_clusters USING hnsw (avg_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
    """)

    conn.commit()

    
    

def connect_to_db():
    """
    Connect to the database and return a database cursor
    """
    default_conn = psycopg.connect(
        dbname="postgres",
        user="myuser",
        password="mypass",
        host="localhost",
        port="5432"
    )
    default_conn.autocommit = True
    default_cur = default_conn.cursor()
    
    # Create new database (ignore error if it exists)
    db_name = DB_NAME
    try:
        default_cur.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(db_name)))
        print(f"Database '{db_name}' created.")
    except psycopg.errors.DuplicateDatabase:
        print(f"Database '{db_name}' already exists.")
    default_cur.close()
    default_conn.close()
    
    conn = psycopg.connect(
        dbname=db_name,
        user="myuser",
        password="mypass",
        host="localhost",
        port="5432"
    )
    create_articles_table(conn)

    return conn
