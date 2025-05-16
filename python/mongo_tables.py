import pymongo

MONGO_CLIENT = pymongo.MongoClient("mongodb://localhost:27017/")
DB = MONGO_CLIENT["newsv2"]
NEWS_ARTICLES = DB["newsArticles"]
