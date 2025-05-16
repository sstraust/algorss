(ns newsv2.postgres
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json])
  (:import [org.postgresql.util PGobject]))

(defn- parse-pgvector [s]
  (let [s (subs s 1 (dec (count s)))
        s (clojure.string/split s #",")]
    (mapv #(Double/parseDouble (clojure.string/trim %)) s)))

(defn- pgobj->clj [^PGobject pgobj]
  (let [type (.getType pgobj)
        value (.getValue pgobj)]
    (case type
      ("json" "jsonb") (json/read-str value :key-fn keyword)
      "vector" (parse-pgvector value)
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
