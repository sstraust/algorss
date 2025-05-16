import psycopg2
from psycopg2.extras import Json
from psycopg2 import sql

# -------------------------------
# Step 1: Connect to default DB to create a new DB
# -------------------------------
default_conn = psycopg2.connect(
    dbname="postgres",
    user="",
    password="",
    host="localhost",
    port="5432"
)
default_conn.autocommit = True
default_cur = default_conn.cursor()

# Create new database (ignore error if it exists)
db_name = "newsv2"
try:
    default_cur.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(db_name)))
    print(f"Database '{db_name}' created.")
except psycopg2.errors.DuplicateDatabase:
    print(f"Database '{db_name}' already exists.")

default_cur.close()
default_conn.close()

# -------------------------------
# Step 2: Connect to the new DB
# -------------------------------
conn = psycopg2.connect(
    dbname=db_name,
    user="",
    password="",
    host="localhost",
    port="5432"
)
cur = conn.cursor()

# -------------------------------
# Step 3: Create table with JSONB
# -------------------------------
cur.execute("""
CREATE TABLE IF NOT EXISTS articles (
    id SERIAL PRIMARY KEY,
    link TEXT NOT NULL,
    source_id TEXT NOT NULL,
    newsData JSONB
)
""")
conn.commit()

# -------------------------------
# Step 4: Insert sample data
# -------------------------------
link = "test_link"
source_id = "test_id"
newsData = {
    "theme": "dark",
    "notifications": {"email": True, "sms": False},
    "dashboard": ["analytics", "reports"]
}

cur.execute(
    "INSERT INTO articles (link, source_id, newsData) VALUES (%s, %s, %s)",
    (link, source_id, Json(newsData))
)
conn.commit()

# -------------------------------
# Step 5: Query and print results
# -------------------------------
cur.execute("SELECT id, link, newsData FROM articles")
rows = cur.fetchall()

print(rows[0][2]["theme"])
for row in rows:
    print("ID:", row[0])
    print("Name:", row[1])
    print("Preferences:", row[2])
    print()

# Clean up
cur.close()
conn.close()


