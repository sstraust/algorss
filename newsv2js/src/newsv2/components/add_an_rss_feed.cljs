(ns newsv2.components.add-an-rss-feed
  (:require
   [reagent.dom :as rdom]
   [reagent.core :as r]
   [easyreagent.components :as er]
   [easyreagent.util :as er-util]))

(def feed-url (r/atom ""))

(defn handle-feed-menu-submit [event]
  (.preventDefault event)
  (er-util/post-request
   "/newRssFeed"
   {:url @feed-url}
   :json-response #(js/alert "successfully added to index!")
   :failure #(js/alert (str "Failed to add feed: " %))))

(defn add-an-rss-feed-menu []
  [:form {:on-submit handle-feed-menu-submit}
   [:h1 "Add an RSS Feed"]
   [:p.mx-2.text-xs.mt-4 "copy paste the url of the feed, e.g. https://www.nytimes.com/services/xml/rss/nyt/HomePage.xml"]
   [er/text-field {:placeholder "feed url"} feed-url]
   [:input.btn.btn-primary.btn-md {:type "submit"}]])

(defn load-add-an-rss-feed []
  (rdom/render [add-an-rss-feed-menu]
               (js/document.getElementById "main-app")))
