import os
import requests
import time
from bs4 import BeautifulSoup
from urllib.parse import urlparse

# Constants
RSS_URL = "http://feeds.foxnews.com/foxnews/latest"
TEST_DIR = "tests"
RESOURCES_DIR = os.path.join(TEST_DIR, "resources")
ARTICLES_DIR = os.path.join(RESOURCES_DIR, "articles")

# Create test directories
os.makedirs(ARTICLES_DIR, exist_ok=True)

def download_file(url, filepath):
    """Downloads content from a URL and saves it to a file."""
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(response.text)
        print(f"Saved: {filepath}")
    except requests.RequestException as e:
        print(f"Failed to download {url}: {e}")

# Download the RSS feed
download_file(RSS_URL, os.path.join(RESOURCES_DIR, "rss.xml"))

# Read RSS feed and extract article URLs
with open(os.path.join(RESOURCES_DIR, "rss.xml"), "r", encoding="utf-8") as f:
    soup = BeautifulSoup(f, "xml")
    items = soup.find_all("item")
    urls = [item.find("link").text for item in items if item.find("link")]  # Extract article URLs

# Download each article
for url in urls:
    parsed_url = urlparse(url)
    filename = parsed_url.path.strip("/").replace("/", "_") or f"article_{int(time.time())}.html"
    filepath = os.path.join(ARTICLES_DIR, filename)
    download_file(url, filepath)
    time.sleep(1)  # Rate-limiting to avoid getting blocked
