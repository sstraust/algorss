(ns newsv2.scratch
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [honey.sql :as sql]
            [clojure.string :as str]
            [newsv2.tables :as tables :refer [db]]
            [monger.collection :as mc]
            [honey.sql.helpers :as h])
  (:import [org.postgresql.util PGobject]))


;; called Clojure

;;  it is a lisp language that runs on the JVM










;; Define a function to convert PGobject to Clojure data structure
(defn- pgobj->clj [^PGobject pgobj]
  (let [type (.getType pgobj)
        value (.getValue pgobj)]
    (case type
      ("json" "jsonb") (json/read-str value :key-fn keyword)
      value)))

;; Extend the IResultSetReadColumn protocol to handle PGobject
(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (pgobj->clj pgobj)))

(def db-spec
  {:dbtype "postgresql"
   :dbname "newsv2"
   :host "localhost"
   :port 5432
   :user "myuser"
   :password "mypass"})

(def article-ids
  (:article_ids
   (first
    (jdbc/query db-spec ["SELECT * FROM recent_clusters ORDER BY ARRAY_LENGTH(article_ids, 1) DESC LIMIT 1;"]))))

(def matching-articles
  (jdbc/query db-spec
              ["SELECT * FROM articles WHERE id = ANY (?)" article-ids]))

(defn hello []
  (str 3))

(defn sharkz []
  (hello))

(+ (sharkz) 4)



;; ideally I want to capture the minimum
;; type requirements from this as a malli schema
(defn square [x]
  (* x x))


(square "3")

(def top-clusters-ids
  (jdbc/query db-spec ["SELECT article_ids FROM recent_clusters ORDER BY ARRAY_LENGTH(article_ids, 1) DESC LIMIT 50;"]))
(defn get-articles-from-ids [article-ids]
  (jdbc/query db-spec
              ["SELECT * FROM articles WHERE id = ANY (?)" article-ids]))

(def top-clusters (map #(get-articles-from-ids (:article_ids %)) top-clusters-ids))


(map (fn [x]
       (count 
        (distinct (map #(get-in % [:news_data :source_id]) (nth top-clusters x)))))
     (range 20))


(println
 (clojure.string/join "\n" (map #(get-in % [:news_data :title]) (map first top-clusters))))


  

(:title (:news_data (first matching-articles)))

(map #(get-in % [:news_data :title]) matching-articles)

;; I should probably write the mongodb id to postgres
(keys (first matching-articles))


(def zz (jdbc/query db-spec ["SELECT * FROM articles LIMIT 1"]))


(def zz (jdbc/query db-spec 
                    ["SELECT * FROM articles WHERE published_date > NOW() - INTERVAL '24 hours'"]))

(def z2 (jdbc/query db-spec 
                    ["SELECT * FROM articles"]))


;; Function to parse a PostgreSQL vector string into a Clojure vector of numbers
(defn- parse-pg-vector
  "Parse a PostgreSQL vector string like '[0.1,0.2,0.3]' into a Clojure vector of doubles"
  [vector-str]
  (when vector-str
    (->> (-> vector-str
             (str/replace #"[\[\]]" "")  ;; Remove square brackets
             (str/split #","))           ;; Split by commas
         (mapv #(Double/parseDouble (str/trim %))))))  ;; Convert each element to a double

;; Function to handle PostgreSQL PGobject of type 'vector'
(defn- pgvector->clj [^PGobject pgobj]
  (let [type (.getType pgobj)
        value (.getValue pgobj)]
    (case type
      "vector" (parse-pg-vector value)
      value)))

;; Extend JDBC protocol to automatically convert vector types to Clojure vectors
(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type (.getType pgobj)]
      (case type
        "vector" (parse-pg-vector (.getValue pgobj))
        "json"   (json/read-str (.getValue pgobj) :key-fn keyword)
        "jsonb"  (json/read-str (.getValue pgobj) :key-fn keyword)
        ;; Return as is for other PG types
        (.getValue pgobj)))))

;; For next.jdbc, extend its ReadableColumn protocol
(defn setup-for-next-jdbc []
  (require '[next.jdbc.result-set :as rs])
  (eval
   '(extend-protocol rs/ReadableColumn
      PGobject
      (read-column-by-label [^PGobject v _]
        (let [type (.getType v)]
          (case type
            "vector" (parse-pg-vector (.getValue v))
            "json"   (json/read-str (.getValue v) :key-fn keyword)
            "jsonb"  (json/read-str (.getValue v) :key-fn keyword)
            (.getValue v))))
      (read-column-by-index [^PGobject v _2 _3]
        (let [type (.getType v)]
          (case type
            "vector" (parse-pg-vector (.getValue v))
            "json"   (json/read-str (.getValue v) :key-fn keyword)
            "jsonb"  (json/read-str (.getValue v) :key-fn keyword)
            (.getValue v)))))))

;; Also handle conversion from Clojure vectors to PG vectors for inserts/updates
(extend-protocol jdbc/ISQLParameter
  clojure.lang.PersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
    ;; Convert Clojure vector to a comma-separated string
    (let [vector-str (->> v
                          (map str)
                          (str/join ","))
          pgobj (doto (PGobject.)
                  (.setType "vector")
                  (.setValue (str "[" vector-str "]")))]
      (.setObject stmt i pgobj))))



(:title (:news_data (first zz)))"Jenkyns at odds with Farage on special needs"

(time
 (map #(:title (:news_data %))
      (find-similar-articles
       db-spec (:title_embedding (first zz))
       :limit 5
       :similarity-method :cosine
       :similarity-threshold 1.4)))


(map #(:similarity %)
 (find-similar-articles
 db-spec (:title_embedding (first zz))
 :limit 5
 :similarity-method :cosine
 :similarity-threshold 1.3))
                       


;; Function to find similar articles by title embedding
(defn find-similar-articles
  "Find articles with similar title embeddings to the given embedding vector."
  [db-spec embedding & {:keys [limit similarity-threshold similarity-method]
                        :or {limit 10
                             similarity-threshold 0.7
                             similarity-method :cosine}}]
  (let [operator (case similarity-method
                   :cosine "<#>"  ; Cosine distance
                   :l2 "<->"      ; L2 distance/Euclidean
                   "<#>")         ; Default to cosine
        
        ;; Convert embedding to PGobject for PostgreSQL vector type
        pg-embedding embedding
        
        ;; Build query with HoneySQL
        query (-> (h/select :*
                           [[:raw (str "title_embedding " operator " ?")] :similarity])
                 (h/from :articles)
                 (h/where [:<= [:raw (str "title_embedding " operator " ?")] 
                           (- 1 similarity-threshold)])  ; Convert similarity to distance
                 (h/order-by [:similarity :asc])  ; Lower distance = higher similarity
                 (h/limit limit)
                 (sql/format))]
    
    ;; The issue is here - HoneySQL generates the SQL and params differently
    ;; Let's use the generated SQL but provide the params explicitly
        (println query)

    (let [[sql & params] query
          ;; We need to explicitly provide both PG embeddings
          final-params (concat [pg-embedding pg-embedding] params)]
      (println sql)
      (println final-params)
      (jdbc/query db-spec (into [sql] final-params)))))

;; Alternative implementation using raw SQL for clarity
(defn find-similar-articles-raw
  "Find articles similar to the given embedding using raw SQL"
  [db-spec embedding & {:keys [limit similarity-threshold similarity-method]
                        :or {limit 10
                             similarity-threshold 0.7
                             similarity-method :cosine}}]
  (let [operator (case similarity-method
                   :cosine "<#>"
                   :l2 "<->"
                   "<#>")
        
        ;; Convert embedding to PGobject
        pg-embedding (doto (PGobject.)
                       (.setType "vector")
                       (.setValue (str embedding)))]
    
    (jdbc/query db-spec
                [(str "SELECT *, (title_embedding " operator " ?) AS similarity 
                      FROM articles 
                      WHERE title_embedding " operator " ? <= " (- 1 similarity-threshold) "
                      ORDER BY similarity ASC 
                      LIMIT ?")
                 pg-embedding pg-embedding limit])))

;; Example function to find articles similar to an existing article
(defn find-articles-similar-to
  "Find articles with similar title embeddings to the given article ID"
  [db-spec article-id & options]
  (let [;; First retrieve the embedding for the reference article
        reference-query ["SELECT title_embedding FROM articles WHERE id = ?" article-id]
        reference-article (first (jdbc/query db-spec reference-query))
        
        ;; Check if article exists
        _ (when-not reference-article
            (throw (ex-info "Article not found" {:article-id article-id})))
        
        ;; Get the embedding
        embedding (.getValue (:title_embedding reference-article))]
    
    ;; Find similar articles, excluding the reference article itself
    (let [similar-articles (apply find-similar-articles db-spec embedding options)]
      (filter #(not= ("id" %) article-id) similar-articles))))








;;;;;;;;;;;;;;;;;;; diversity reranking 
(def best-candidate (second (first (sort-by (comp :score second) > valid-candidates))))


(defn calculate-embedding-dist-for-articles [embedding postgres-article-ids]
  (jdbc/query db-spec ["SELECT mongo_id, id, title_embedding <=> ? AS embedding_similarity FROM ARTICLES WHERE id = ANY (?)"
                       (make-pgvector embedding)
                       (pg-array (jdbc/get-connection db-spec)
                                  "Integer"
                                  postgres-article-ids)]))


(def embedding-dists
  (calculate-embedding-dist-for-articles
   (get-avg-article-embedding [(:id best-candidate)])
   (map :id (vals valid-candidates))))

(defn key-by
  "Returns a map keyed by the result of applying `f` to each element in `coll`."
  [f coll]
  (into {} (map (fn [x] [(f x) x]) coll)))

(def embedding-dists-map (key-by :mongo_id embedding-dists))

;; now I want to _SANITY TEST_ embedding dists map so that I can be sure
;; that it makes sense

(:embedding_similarity (get embedding-dists-map (first (first valid-candidates))))

(def diversity-penalty 0.1)

(def resorted-candidates 
  (for [[mongo-id score] valid-candidates
        :when (:embedding_similarity (get embedding-dists-map mongo-id))]
    [mongo-id (- score (:embedding_similarity (get embedding-dists-map mongo-id)))]))



(:title
 (get-article-from-mongo-id
(first (second (sort-by second > resorted-candidates))) nil))

(sort


valid-candidates
 

(get-postgres-ids-from-mongo-ids [


(calculate-embedding-for-articles


(:title
 (get-article-from-mongo-id
  (first (first (sort-by second > valid-candidates))) nil))

(get-article-from-mongo-id
 (first (first valid-candidates))
 
                           
         
      

(sort-by second > @candidates-map)

(first candidate-entries)

     
    
;;;;;;;;;;;;;;;;; diversity reranking
