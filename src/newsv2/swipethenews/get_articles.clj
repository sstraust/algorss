(ns newsv2.swipethenews.get-articles
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [easyreagentserver.core :as er-server]
   [monger.collection :as mc]
   [newsv2.postgres :refer [db-spec]]
   [newsv2.tables :as tables :refer [db]]
   [libpython-clj2.python :as py]
   [libpython-clj2.python.np-array]
   [libpython-clj2.require :refer [require-python]]
   [malli.core :as m])
  (:import
   [org.bson.types ObjectId]
   [org.postgresql.util PGobject]))

(require-python 'numpy)

(defn make-pgvector
    "Wraps a Clojure vector of numbers into a PGobject of type 'vector'."
    [floats]
    (doto (PGobject.)
      (.setType  "vector")
      (.setValue (str "[" (str/join "," floats) "]"))))


(defn pg-array [conn base-type coll]
  (let [arr-type (case (.toLowerCase base-type)
                       "integer" Integer
                       "text" String
                       String)
        java-arr (into-array arr-type coll)]
    (.createArrayOf conn base-type java-arr)))


(defn avg [inp]
  (/ (apply + inp)
     (count inp)))

(defn hours-since [date]
  (/ (- (System/currentTimeMillis) (.getTime date))
     (* 1000 60 60.0)))


(defn return-articles-as-json [articles]
  (er-server/json-response
   {:stories
    (map #(select-keys (assoc % :mongo_id (str (:_id %)))
                       [:title :description :og_image_tag :mongo_id])
         articles)}))


;;;;;;;;; Initial Clusters
(defn get-articles-from-postgres-ids [postgres-ids avg-embedding]
  (let [mongo-ids (jdbc/query db-spec
                              ["SELECT mongo_id, title_embedding <=> ? AS center_similarity FROM articles WHERE id = ANY (?)"
                               (make-pgvector avg-embedding) postgres-ids])]
    (remove #(nil? (:_id %))
    (for [{id :mongo_id
           center-similarity :center_similarity} mongo-ids]
      (do (assoc
           (mc/find-map-by-id db tables/articles-table (ObjectId. id))
           :cluster_center_similarity center-similarity))))))

(defn get-top-db-clusters [limit]
  (let [db-result (jdbc/query db-spec ["SELECT article_ids, avg_embedding FROM recent_clusters ORDER BY ARRAY_LENGTH(article_ids, 1) DESC LIMIT ?;"
                                       limit])]
    (remove empty? 
            (map #(get-articles-from-postgres-ids
                   (:article_ids %)
                   (:avg_embedding %))
                 db-result))))

(defn avg-hours-since-published [cluster]
  (avg (map hours-since (map :published_date cluster))))

(defn count-unique-sources [cluster]
  (count (distinct (map :source_id cluster))))

(defn calculate-cluster-score [cluster]
  (/
   (+ (count-unique-sources cluster)
      (* 20 (if (some :og_image_tag cluster) 1 0)))
   
   (Math/pow (+ 2 (avg-hours-since-published cluster)) 0.3)))


(defn get-article-score [article]
  (/ (+ (:cluster_center_similarity article)
        (* 2 (if (:og_image_tag article) 1 0)))
     (Math/pow (+ 2 (hours-since (:published_date article))) 0.3)))

(defn get-best-article [cluster]
  (first (sort-by get-article-score > cluster)))


o(defn get-initial-top-articles []
  (let [top-db-clusters (remove empty? (get-top-db-clusters 50))
        top-clusters (sort-by calculate-cluster-score > top-db-clusters)]
    (map get-best-article top-clusters)))

(defn get-top-articles [params]
  (return-articles-as-json (get-initial-top-articles)))
;; 
;; (get-top-articles zz)

(comment
  (def top-clusters (remove empty? (get-top-db-clusters 50)))

  (map #(filter (fn [x] (not (:published_date x))) %) top-clusters)

  )
;;;;;;;; End Initial Clusters



;;;;;;;; Recommended Articles
(defn get-avg-article-embedding [article-ids]
  (let [article-ids (pg-array (jdbc/get-connection db-spec)
                                  "Integer"
                                  article-ids)
        embeddings (map :title_embedding
                        (jdbc/query db-spec
                                    ["SELECT title_embedding FROM articles WHERE id = ANY (?)" article-ids]))]
    (numpy/mean (into [] embeddings) 0)))

(defn get-article-ids-from-recommended [curr-article-ids limit]
  (let [avg-embedding (get-avg-article-embedding curr-article-ids)]
    (jdbc/query db-spec ["SELECT mongo_id, title_embedding <=> ? AS embedding_similarity FROM articles ORDER BY title_embedding <=> ? LIMIT ?",
                         (make-pgvector avg-embedding)
                         (make-pgvector avg-embedding)
                         limit])))

(defn get-article-from-mongo-id [mongo-id extra-info]
  (merge (mc/find-map-by-id db tables/articles-table (ObjectId. mongo-id))
         extra-info))

(defn get-postgres-ids-from-mongo-ids [mongo-ids]
  (let [mongo-ids (map str mongo-ids)]
    (map :id (jdbc/query db-spec
                         ["SELECT id FROM articles WHERE mongo_id = ANY (?)"
                          (pg-array (jdbc/get-connection db-spec) "TEXT" mongo-ids)]))))
                           
        
(defn get-articles-from-recommended [curr-article-ids limit]
  (for [{mongo-id :mongo_id
         embedding-similarity :embedding_similarity} (get-article-ids-from-recommended curr-article-ids limit)]
    (get-article-from-mongo-id
     mongo-id
     {:embedding_similarity embedding-similarity})))
                               

(defn calculate-recommended-score [article]
  (/ (:embedding_similarity article)
     (Math/pow (+ 2 (hours-since (:published_date article))) 0.3)))


(defn get-recommended-articles [{{:keys [liked-ids]} :params :as params}]
  (let [postgres-ids (get-postgres-ids-from-mongo-ids liked-ids)
        articles (sort-by calculate-recommended-score >
                          (get-articles-from-recommended postgres-ids 50))]
    (return-articles-as-json articles)))


;;;;;;;;;; End of recommended articles
(comment

  (case (.toLowerCase "TEXT")
        "integer" Integer
        "text" String
        String)
  
  (pg-array (jdbc/get-connection db-spec) "TEXT" (map str a1))

  (into-array String (map str a1))

  (pg-array 
  
  (get-postgres-ids-from-mongo-ids a1)
))
