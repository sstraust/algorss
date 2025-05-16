(ns newsv2.swipethenews.recommended-rrf
  (:require
   [clojure.java.jdbc :as jdbc]
   [newsv2.postgres :refer [db-spec]]
   [newsv2.swipethenews.get-articles :as get-articles
    :refer [make-pgvector get-avg-article-embedding get-postgres-ids-from-mongo-ids
            get-article-from-mongo-id pg-array]]
   [libpython-clj2.python :as py]
   

   [libpython-clj2.python.np-array]
   [libpython-clj2.require :refer [require-python]]))

(require-python 'numpy.random)
(require-python '[numpy :as np])
(require-python '[operator :as op])


(defn get-articles-near-embedding [embedding limit]
  (let [pg-embedding (make-pgvector embedding)]
    (into [] (jdbc/query db-spec ["SELECT id, mongo_id, title_embedding <=> ? AS embedding_similarity FROM articles ORDER BY title_embedding <=> ? LIMIT ?",
                         pg-embedding
                         pg-embedding
                         limit]))))


(defn get-candidate-entries-with-initial-ranking [postgres-ids]
  (let [candidate-entries (for [article-id postgres-ids]
                            (get-articles-near-embedding
                             (get-avg-article-embedding [article-id]) 50))
        candidates-map (atom {})]
    (doseq [candidate-entry-result candidate-entries]
      (doseq [[idx entry] (map-indexed vector candidate-entry-result)]
        (swap! candidates-map update-in [(:mongo_id entry) :score] (fnil + 0) (/ 1.0 (+ idx 2)))))
    (doseq [candidate-list candidate-entries]
      (doseq [entry candidate-list]
        (swap! candidates-map update-in [(:mongo_id entry)] merge (dissoc entry :embedding_similarity))))
    @candidates-map))

(defn get-valid-candidates [initial-candidates]
  (into {}
        (remove (fn [[k v]] (contains? (set example-ids) (:mongo_id v)))
                initial-candidates)))


(defn re-weight-article [article]
  (let [article-info (get-article-from-mongo-id (:mongo_id article) {})
        article (update article :score #(Math/pow % 0.05))
        article (if (or (not (:og_image_tag article-info))
                        (empty? (:og_image_tag article-info)))
                  (assoc article :score 0.0000000001)
                  article)]
    article))



(defn cluster-score [cluster liked-ids]
  (let [initial-score (get-articles/calculate-cluster-score cluster)
        is-seen-before (some #(contains? (set liked-ids) (str (:_id %))) cluster)]
    (* initial-score
       0.15
       (if is-seen-before 0.01 1))))

(defn get-weighted-cluster-articles [liked-ids postgres-ids]
  (let [clusters (get-articles/get-top-db-clusters 50)
        
        clusters-with-score (for [cluster clusters] {:cluster cluster
                                                     :score (cluster-score cluster liked-ids)})
        articles-with-score (for [cluster clusters-with-score]
                              {:article
                               (get-articles/get-best-article (remove #(contains? (set liked-ids) (str (:_id %))) (:cluster cluster)))
                               :score (:score cluster)})]

    (into {}
          (for [article articles-with-score]
            [(str (:_id (:article article))) {:score (:score article)
                                              :mongo_id (str (:_id (:article article)))}]))))



(defn get-top-choices [valid-candidates]
  (let [choices (into [] (vals valid-candidates))
        choices (into [] (pmap re-weight-article choices))
        weights-raw (np/array (into []
                                    (map :score choices)))
        weights (op/truediv weights-raw (py/py. weights-raw sum))]
    (numpy.random/choice choices :size 10 :replace false
                         :p weights)))

(defn get-recommended-articles [{{:keys [liked-ids]} :params :as params}]
  (def inp params)
  (let [liked-ids (into [] (take 10 (reverse liked-ids)))
        postgres-ids (get-postgres-ids-from-mongo-ids liked-ids)
        initial-candidates (get-candidate-entries-with-initial-ranking postgres-ids)
        valid-candidates (get-valid-candidates initial-candidates)
        all-vandiadate (merge-with (fn [val1 val2] (assoc val1 :score (+ (:score val1) (:score val2))))
                                valid-candidates (get-weighted-cluster-articles liked-ids postgres-ids))
        top-choice-ids (get-top-choices valid-candidates)]
    (get-articles/return-articles-as-json
     (map #(get-article-from-mongo-id
            (:mongo_id %)
            {:recommender_score (:score %)})
          top-choice-ids))))

(comment
  (def example-ids a1)
  (def postgres-ids (get-postgres-ids-from-mongo-ids example-ids))

         )




;; (get-recommended-articles inp)

;; now you need to""
;;  ---- add other ranking features, like og_img_tag to the overall ranking
;;  ------ it doesn't really hit right
;;  ------ because -- there aren't that many articles on one topic
;;  ------ but your algorithm super zeroes in on the user's preferences too fast
;;  ------ and it doesn't have enough --- like ---  spiciness
;;  ------ you need to make sure there's enough stuff in the candidate choices, even stuff that's sort of related
;;  ------ and the zoom in doesn't hit too hard
;; ------ ok, so I think your algorithm is getting more reasonable, you still need to:
;; ------------ get news from more sources
;; ------------ add random articles from the database to the example query-set, and then rank them based on
;; -------------- liked-article similarity
