import json
from pathlib import Path

# Load JSON Schemas
def load_schema(filename):
    schema_path = Path(__file__).parent / filename
    with open(schema_path, 'r', encoding='utf-8') as f:
        return json.load(f)

article_schema = load_schema("../schema/article.schema.json")
article_source_schema = load_schema("../schema/article_source.schema.json")

