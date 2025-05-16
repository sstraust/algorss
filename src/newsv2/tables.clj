(ns newsv2.tables
  (:require [monger.core :as mg]))

(def conn (mg/connect))
(def db (mg/get-db conn "newsv2"))

(def rss-sources-table "rssSourcesTable")
(def articles-table "newsArticles")
