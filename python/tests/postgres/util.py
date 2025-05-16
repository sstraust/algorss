import pytest
import testing.postgresql
import psycopg
from pgvector.psycopg import register_vector
import postgres.tables

@pytest.fixture(scope='function')
def db_conn():
    # Create a temporary PostgreSQL instance
    postgresql = testing.postgresql.Postgresql()
    dsn = postgresql.dsn()
    conn = psycopg.connect(
        f"dbname={dsn['database']} user={dsn['user']} host={dsn['host']} port={dsn['port']}"
    )

    postgres.tables.create_articles_table(conn)
    postgres.tables.create_recent_clusters_table(conn)
    register_vector(conn)

    
    yield conn
    # Teardown: close connection and stop the temp Postgres
    conn.close()
    postgresql.stop()

