from util import catch_all_exceptions
import util
import feedparser
import logging
import pymongo
from pymongo import UpdateOne
import re
import requests
from bs4 import BeautifulSoup
import time
import sys
from typing import Optional, Dict, Any, Union, Tuple
from datetime import datetime
from timeout_decorator import timeout
import postgres.update_articles

from mongo_tables import DB
import mongo_tables

# util.parsedate
import importlib
importlib.reload(util)
from additional_article_parsers import header_image

additional_article_parsers = [header_image.parse_header_image]

def request_page_contents(url: str, max_size: int = 1024*1024*10, timeout: int = 5) -> Optional[bytes]:
    try:
        # Open the connection in streaming mode with a timeout
        with requests.get(url, stream=True, timeout=timeout) as response:
            response.raise_for_status()  # Raise an exception for HTTP errors
            content = b""
            for chunk in response.iter_content(chunk_size=1024):
                content += chunk
                if len(content) > max_size:
                    print("Response size exceeded maximum limit.")
                    return None
            return content[:max_size]  # Return only up to max_size
    except requests.exceptions.Timeout:
        print("The request timed out.")
        return None
    except requests.exceptions.RequestException as e:
        print(f"An error occurred: {e}")
        return None

def get_html_response(url: str):
    page_response = request_page_contents(url)
    if page_response and len(page_response) > 10*1024*1024:
        logging.warning("failed to parse " + url + "response too long")
        page_response = None
    soup = BeautifulSoup(page_response, "lxml") if page_response else None
    return {"page_response": page_response, "soup": soup}


@catch_all_exceptions
def get_article_db_update(article, source_id, collection, feed) -> Optional[Tuple[UpdateOne, Dict[str, Any], Dict[str, Any]]]:
    basic_info = {"link": article.get('link', None),
                  "published_date": util.parsedate(article.get('published', article.get('updated', None))),
                  "title": article.get('title', None),
                  "author": article.get('author', None),
                  "description": article.get('description', None),
                  "source_id": source_id,
                  "self_reported_feed_title": feed.get("feed", {}).get("title", "Unknown")}

    if not basic_info["link"]:
        logging.info(f"Skipping article because it lacks a valid link: {basic_info['title']}")
        return None


    existing_article = collection.find_one({
        "link": basic_info["link"],
        "source_id": source_id})

    if not existing_article:
        page_data = get_html_response(basic_info["link"])
    else:
        page_data = None

    if not basic_info["published_date"]:
        if existing_article:
            basic_info["published_date"] = existing_article["published_date"]
        else:
            basic_info["published_date"] = datetime.now().isoformat()
    

    data_to_update = {}
    for article_parser in additional_article_parsers:
        try:
            article_parser = timeout(60*1)(article_parser) # bail if the article parser takes too long
            data_to_update = data_to_update | article_parser(basic_info, page_data, existing_article)
        except Exception as e:
            pass
            
    data_to_update = data_to_update | basic_info

    update = UpdateOne(
                {"link": basic_info["link"],
                 "source_id" : source_id},
                {"$set": data_to_update},
                upsert = True)

    return {"mongodb_update": update,
            "key_info": {"link": basic_info["link"],
                         "source_id": source_id,
                         "published_date": basic_info["published_date"]},
            "data_to_update": data_to_update}


def is_valid_feed(url):
    feed = feedparser.parse(url)
    if "entries" not in feed:
        return {"outcome" : "fail",
                "reason" : "Unable to parse article entries from feed."}
    article_infos = []
    for article in feed.entries:
        article_infos.append(
             {"link": article.get('link', None),
                          "published_date": util.parsedate(article.get('published', article.get('updated', None))),
                          "title": article.get('title', None)})
    if not [x for x in article_infos if x["link"]]:
        return {"outcome": "fail",
                "reason" : "No entries in this feed with a link to an article"}
    if not [x for x in article_infos if x["published_date"]]:
        return {"outcome": "fail",
                "reason" : "No entries in this feed containing a publication date"}
    if not [x for x in article_infos if x["title"]]:
        return {"outcome": "fail",
                "reason" : "No entries in this feed containing a title"}

    if not [x for x in article_infos if (x["link"]
                                         and x["title"]
                                         and x["published_date"])]:
        return {"outcome": "fail",
                "reason" : "No entries in this feed containing a valid article (with a link, a publication date, and a title)"}
    return {"outcome" : "success",
            "info" : {"self_reported_name" : feed.get("feed", {}).get("title", "Unknown"),
                      "last_updated" : list(reversed(sorted([x["published_date"] for x in article_infos])))[0].timestamp(),
                      "description" : feed.get("feed", {}).get("description", "Unknown")}}
            
        

    
@catch_all_exceptions
@timeout(60*10) # Bail if it takes more than 10 minutes to process a feed
def process_feed(url, source_id, collection=mongo_tables.NEWS_ARTICLES):
    try:
        logging.info("Parsing feed for: " + source_id + " at url " + url)

        feed = feedparser.parse(url)
        if "entries" not in feed:
            logging.warning("No entries found in RSS feed at: " + url)
            return


        updates = []
        for article in feed.entries:
            article_db_update = get_article_db_update(article, source_id, collection, feed)
            if article_db_update:
                updates.append(article_db_update)
        if updates:
            try:
                updates_only = [x["mongodb_update"] for x in updates]
                result = collection.bulk_write(updates_only)
                for update in updates:
                    postgres.update_articles.copy_update_to_db(
                        postgres.update_articles.CONN,
                        update["key_info"]["source_id"],
                        update["key_info"]["link"],
                        update["key_info"]["published_date"],
                        update["data_to_update"])
                
                article_source_collection = DB["rssSourcesTable"]
                article_source_collection.update_one(
                    {"unique_id": source_id},
                    {"$set": {
                        "last_fetch_attempt.num_fetched": len(updates),
                        "last_fetch_attempt.last_attempt_date": datetime.now(),
                        "last_fetch_attempt.last_attempt_result": "success",
                    }
                 }
                )
            
            except Exception as e:
                print(e)
                logging.error(f"error during bulk write: {e}")
    except Exception as e:
        print(e)
        article_source_collection = DB["rssSourcesTable"]
        article_source_collection.update_one(
            {"unique_id": source_id},
            {"$set": {
                "last_fetch_attempt.num_fetched": 0,
                "last_fetch_attempt.date": datetime.now(),
                "last_fetch_attempt.last_attempt_result": "failure" + str(e)
                }
             }
        )
                
    


def get_user_data_sources():
    user_sources_collection = DB["rssSourcesTable"]
    return [x for x in user_sources_collection.find({})]
        

# get_data_sources()

# Main execution
if __name__ == "__main__":
    method_to_run = sys.argv[1]
    match method_to_run:
        case "user_sources":
            while True:
                for source in get_user_data_sources():
                    source_link = source["url"]
                    source_id = source["unique_id"]
                    process_feed(source_link, source_id)
                # 10 minutes
                print("Done with loop. Waiting for 10 minutes")
                time.sleep(60*10)
        case "test_feed":
            process_feed("https://www.youtube.com/feeds/videos.xml?channel_id=UC--TKxqP8xJNymgrLe-thhA" ,"uThermal Youtube")
