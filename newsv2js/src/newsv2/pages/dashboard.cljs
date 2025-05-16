(ns newsv2.pages.dashboard 
  (:require
   [easyreagent.util :as er-util]
   [reagent.dom :as rdom]
   [reagent.core :as r]))

(def data (r/atom nil))

(defn format-timestamp [epoch-millis]
  (let [d (js/Date. (* 1000 epoch-millis))]
    (.toLocaleString d)))

(defn dashboard []
  [:v-box
   [:div.text-2xl.mb-4 "Dashboard"]
   [:table.ml-4
    [:thead.text-secondary.text-sm
     [:tr
      [:td "Source Name"]
      [:td "Last Updated"]
      [:td "Last Sync Attempt"]
      [:td "Last Sync Result"]
      [:td "Num Fetched"]]]
    [:tbody 
    (for [article-source (:article-source-data @data)]
      [:tr
       [:td (:self_reported_name article-source)]
       [:td (format-timestamp (:last_updated article-source))]
       [:td (:last_attempt_date (:last_fetch_attempt article-source))]
       [:td (:last_attempt_result (:last_fetch_attempt article-source))]
       [:td (:num_fetched (:last_fetch_attempt article-source))]])]]]
   )


(defn load-dashboard []
  (er-util/post-request
   "/fetchDashboardData"
   {}
   :failure #(js/alert "fetch failed")
   :json-response (fn [json] (reset! data json)))
  (rdom/render [dashboard]
               (js/document.getElementById "main-app")))
