import os
import unittest
import warnings
from unittest.mock import patch, MagicMock
import feedparser
import mongomock
import collect_news
from urllib.parse import urlparse
import mongo_tables


def generate_filename(url: str) -> str:
    parsed_url = urlparse(url)
    filename = parsed_url.path.strip("/").replace("/", "_")
    return filename if filename else f"article_{int(time.time())}.html"

def load_sample_file(filename: str) -> str:
    try:
        base_dir = os.path.join(os.path.dirname(__file__), "resources")
    except NameError:
        # Fallback if __file__ is not defined
        base_dir = os.path.join(os.getcwd(), "resources")
    filepath = os.path.join(base_dir, filename)
    with open(filepath, "r", encoding="utf-8") as f:
        return f.read()


class TestProcessFeedEndToEnd(unittest.TestCase):
    def setUp(self) -> None:
        self.mongo_client = mongomock.MongoClient()
        mongo_tables.DB = self.mongo_client["testdb"]
        mongo_tables.NEWS_ARTICLES = mongo_tables.DB["newsArticles"]
        self.sample_rss = load_sample_file("rss.xml")
        self.fake_feed = feedparser.parse(self.sample_rss)
        
        self.article_html = {}
        for entry in self.fake_feed.entries:
            filename = os.path.join("articles", generate_filename(entry.get("link", "")))
            self.article_html[entry.get("link")] = load_sample_file(f"{filename}")

    def fake_request_page_contents(self, url: str, max_size: int = 10 * 1024 * 1024, timeout: int = 5):
        if url in self.article_html:
            return self.article_html[url].encode("utf-8")
        return None

    @patch("python.collect_news.feedparser.parse")
    @patch("python.collect_news.request_page_contents")
    def test_process_feed_e2e_from_files(self, mock_request_page_contents, mock_feedparser_parse):
        # Patch feedparser.parse to return our fake feed.
        mock_feedparser_parse.return_value = self.fake_feed
        # Patch request_page_contents to load file-based HTML content.
        mock_request_page_contents.side_effect = self.fake_request_page_contents

        collect_news.process_feed("dummy_url.xml", "fake_source", mongo_tables.NEWS_ARTICLES)

        collection = mongo_tables.DB["newsArticles"]
        articles = list(collection.find({}))

        # Check that there are as many articles as entries in our fake feed.
        self.assertEqual(len(articles), len(self.fake_feed.entries))


if __name__ == "__main__":
    unittest.main()
