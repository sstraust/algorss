(ns newsv2.run-web-server
  (:require
   [compojure.core :refer [defroutes POST]]
   [easyreagentserver.core :as er-server]
   [newsv2.dashboard :as dashboard]
   [newsv2.home-page :as home-page]
   [newsv2.manage-indexed-feeds :as manage-indexed-feeds]
   [newsv2.swipethenews.recommended-rrf :as recommended-rff]
   [newsv2.swipethenews.get-articles :as get-articles]))



(defroutes routes
  (home-page/homepage-routes
   ["/" "/addFeedMenu" "/dashboard"
    "/swipethenews"])
  (POST "/newRssFeed" params (manage-indexed-feeds/add-anonymous-feed-to-index params))
  (POST "/fetchDashboardData" params (dashboard/fetch-dashboard-data params))
  (POST "/swipethenews/getTopRecommendations"
      params (let [output (get-articles/get-top-articles params)]
               output))

  (POST "/swipethenews/getCustomRecommendations"
      params (let [output (recommended-rff/get-recommended-articles params)]
               output))
  )

(defn run-web-server [input-mode]
  (when input-mode (reset! er-server/MODE input-mode))
  (er-server/run-web-server
   "newsv2js" routes
   {:port 8006
    :join? false
    :headerBufferSize 1048576}))


;; (run-web-server :dev)
