(ns rosado.cljs-base
  (:use [hiccup.middleware :only [wrap-base-url]]
        compojure.core)
  (:require [hiccup
             [page :refer [html5]]
             [element :refer [javascript-tag]]
             [page :refer [include-js]]]
            [compojure
             [route :as route]
             [handler :as handler]
             [response :as response]]
            [ring.adapter.jetty :as jetty]))

(defn include-clojurescript
  [path]
  (include-js path))

(defn index-page
  []
  (html5
   [:head
    [:title "cljs-base"]
    (include-clojurescript "/js/main.js")]
   [:body
    [:h1 "Hey, dude"]]))

(defroutes main-routes
  (GET "/" [] (#'index-page))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> (handler/site #'main-routes)
      (wrap-base-url)))

(defn run
  []
  (jetty/run-jetty #'rosado.cljs-base/app {:port 3500 :join? false}))
