(ns newsv2.pages.swipethenews.home 
  (:require
   [easyreagent.util :as er-util]
   [reagent.core :as r]
   [easyreagent.components :as er]
   [reagent.dom :as rdom]
   [goog.events :as events]
   [goog.events.EventType :as EventType]))

(def curr-articles-list (r/atom []))

;; ideally you store these on the backend
;; maybe you store the local batch, and you send on update?
(def swipe-info (r/atom {:liked-ids []
                         :disliked-ids []}))

(def is-loading-articles (atom false))


(defn load-stories []
  (er-util/post-request
   "/swipethenews/getTopRecommendations"
   {}
   :json-response #(reset! curr-articles-list (take 10 (:stories %)))
   :failure #(js/alert "failed to fetch")))

(defn load-recommended-stories []
  (reset! is-loading-articles true)
  (er-util/post-request
      "/swipethenews/getCustomRecommendations"
      {:liked-ids (:liked-ids @swipe-info)}
    :json-response #(do
                      (reset! is-loading-articles false)
                      (reset! curr-articles-list
                             (concat (take 2 @curr-articles-list)
                                     (:stories %))))
    :failure #(do (js/alert "failed to fetch")
                  (reset! is-loading-articles false))
                  ))


(defn maybe-refresh []
  (when (and (< (count @curr-articles-list) 7)
             (not @is-loading-articles))
    (load-recommended-stories)))
    
           
(defn swipe-left []
  (maybe-refresh)
  (swap! swipe-info update :disliked-ids conj (:mongo_id (first @curr-articles-list)))
  (swap! curr-articles-list rest))

(defn swipe-right []
  (maybe-refresh)
  (swap! swipe-info update :liked-ids conj (:mongo_id (first @curr-articles-list)))
  (swap! curr-articles-list rest))


(defn handle-keydown [e]
  (.log js/console e)
  (let [key (.-key e)]
    (cond
      (= key "ArrowLeft") (swipe-left)
      (= key "ArrowRight") (swipe-right))))


(defn preloaded-images []
  [:div
   (for [article (take 10 @curr-articles-list)]
     [:img {:src (:og_image_tag article)}])])


(defn home []
  (let [curr-article (first @curr-articles-list)]
    [:div.hero.mt-6
     {:style {:min-height "25vh"}}
     [:div {:style {:visibility "hidden"
                    :height 0
                    :overflow "hidden"}} [preloaded-images]]
     [:div.hero-content.max-w-2xl
      [:v-box
       (if (empty? @curr-articles-list)
         [:div "Loading..."]
         [:div.text-2xl.font-semibold (:title curr-article)])
       (if (:og_image_tag curr-article)
         [:img.object-contain {:src (:og_image_tag curr-article)}])
       [:h-box.w-full.justify-between.mt-2
        [:v-box
         [:div.kbd.kbd-lg "←"]]
        [:v-box
         [:div.kbd.kbd-lg "→"]]]]]]))

(defn load-home []
  (load-stories)
  (events/listen js/document EventType/KEYDOWN handle-keydown)
  (rdom/render [home]
               (js/document.getElementById "main-app")))

;; (load-home)
;; "hero hero-content"
;; (load-recommended-stories)
