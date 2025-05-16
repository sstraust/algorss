(ns newsv2.core
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [newsv2.components.add-an-rss-feed :as add-an-rss-feed]
   [newsv2.pages.dashboard :as dashboard]
   [newsv2.pages.swipethenews.home :as swipethenews.home]
   [reagent.dom :as rdom]))

(defn load-main-page []
  (rdom/render [:div.ml-4 "homepage"]
               (js/document.getElementById "main-app")))

(defn ^:export load-page []
  (case (js* "window.location.pathname")
    "/" (load-main-page)
    "/addFeedMenu" (add-an-rss-feed/load-add-an-rss-feed)
    "/dashboard" (dashboard/load-dashboard)
    "/swipethenews" (swipethenews.home/load-home)
    ))

  
(load-page)


(def curr-css (atom nil))

(defn reload-stylesheets []
  (cljs.core.async.macros/go
    (let [output (:body (<! (http/get (str "resources/global_output.css?v=" (rand-int 1000000)))))]
      (when (not (= @curr-css output))
        (js/reloadStylesheets))
      (reset! curr-css output))))

(defn reload-css []
  (js/setInterval
   (fn [] (reload-stylesheets)) 500))

(js/setTimeout (fn []
                 (when (= js/mode "dev")
                   (reload-css)))
               3000)
