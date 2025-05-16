(ns newsv2.manage-indexed-feeds
  "Manage what feeds are indexed by the RSS article collection service"
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [easyreagentserver.core :as er-server]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [monger.collection :as mc]
   [newsv2.tables :as tables :refer [db]]))

(def sys (py/import-module "sys"))
(def os (py/import-module "os"))
(def cwd (py/call-attr os "getcwd"))

(when-not (some #(= % cwd) (py/get-attr sys "path"))
  (py/call-attr (py/get-attr sys "path") "insert" 0 (str cwd "/python")))

(require-python 'collect_news)



;; this should return a _reason_ why the feed wasn't parsed
(defn process-source-data [url]
  (collect_news/is_valid_feed url))

;; add-feed-to-index should return a json with:
;; the source id str
;; the title
;; and the description
(defn add-anonymous-feed-to-index [{{:keys [url]} :params}]
  (let [source-id-str url]
  (if (mc/find-one-as-map db tables/rss-sources-table {:unique_id source-id-str})
    (er-server/failure-response "source id string already exists!")
    (let [;; first check if we already index this URL
          is-url-present (mc/find-one-as-map db tables/rss-sources-table {:url url})
          ;; then verify we can actually parse it
          parsed-source-data (or is-url-present
                                 (process-source-data url))
          parsed-source-info (get parsed-source-data "info")]
      (cond
        is-url-present (er-server/json-response
                        (select-keys is-url-present
                                     [:unique_id :self_reported_name :description]))
        
        (=  (get parsed-source-data "outcome") "fail")
        (er-server/failure-response (get parsed-source-data "reason"))

        :else
        (do (mc/update db tables/rss-sources-table
                       {:url url
                        :unique_id source-id-str}
                       (merge
                        {:unique_id source-id-str
                         :display_name (get parsed-source-info "self_reported_name")
                         :url url
                         :owner "anonymous"}
                        {:self_reported_name (get parsed-source-info "self_reported_name")
                         :last_updated (get parsed-source-info "last_updated")
                         :description (get parsed-source-info "description")})
                       {:upsert true})
            (let [curr-entry (mc/find-one-as-map db tables/rss-sources-table
                                                 {:url url
                                                  :unique_id source-id-str})]
              (er-server/json-response
               (select-keys curr-entry
                            [:unique_id :self_reported_name :description])))))))))

;; TODO need to write a design for
;; how to deduplicate RSS feeds
;; during data collection and article infos
;; for now, duplication will be allowed, and
;; everything will be considered separate
(defn get-source-urls-from-csv [csv-name]
  (let [raw-csv (with-open [reader (io/reader csv-name)]
                  (doall (csv/read-csv reader)))
        trimmed-csv (map #(map string/trim %) raw-csv)]
    (for [[url sourceName] trimmed-csv]
      url)))


(defn add-feeds-from-csv [csv-name]
  (let [urls (get-source-urls-from-csv csv-name)]
    (doseq [url urls]
      (add-anonymous-feed-to-index {:params {:url url}}))))

(defn add-feeds-from-csv-main [{csv-name :csv-name}]
  (add-feeds-from-csv csv-name))

(comment

  (add-feeds-from-csv
   "resources/newsSources/major_news_outlets.csv")
   
  (add-feed-to-index
   {:params {:url "https://www.nytimes.com/services/xml/rss/nyt/HomePage.xml"}})
  (process-source-data "https://www.nytimes.com/services/xml/rss/nyt/HomePage.xml")

  

  )
