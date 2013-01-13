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
  (javascript-tag "var CLOSURE_NO_DEPS = true;")
  (include-js path))

(defn index-page
  []
  (html5
   [:head
    [:title "cljs-base"]]
   [:body
    [:h1 "Arrowlets"]
    [:p "See the js console!"]
    [:p#click-count "Click me"]
    [:p#two-clicks "Two Clicks"]
    [:p#different-two-clicks "Different Two Clicks"]
    [:p [:span#heads "Heads"] [:span#tails "Tails"]]
    [:div#bindTest "bind test"]
    (include-clojurescript "/js/main.js")]))

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
