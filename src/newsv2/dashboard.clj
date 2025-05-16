(ns newsv2.dashboard
  (:require [easyreagentserver.core :as er-server]
            [newsv2.tables :as tables :refer [db]]
            [monger.collection :as mc]))


(defn fetch-article-source-data []
  (map #(dissoc % :_id)
   (mc/find-maps db tables/rss-sources-table {})))


(defn fetch-dashboard-data [_]
  (er-server/json-response {:article-source-data (fetch-article-source-data)}))



