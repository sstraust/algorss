(ns newsv2.home-page
  (:require [hiccup.page :refer [include-js include-css html5]]
            [compojure.core :refer [GET]]
            [easyreagentserver.core :as er-server]
            [clojure.data.json :as json]))


(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (str "<script>mode=" (json/write-str @er-server/MODE) "</script>")
   (include-css "/resources/global_output.css")])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    [:div {:id "main-app"}]
    (include-js "/resources/reload_css.js")
    (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js")
    (include-js (str "/out/main.js?v=" (rand-int 100000)))]))

(defn get-main-page []
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})


(defn return-home-page 
  [route]
  (GET route _ (get-main-page)))

(defn homepage-routes [routes]
  (apply compojure.core/routes (map return-home-page routes)))
