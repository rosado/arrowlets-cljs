(defproject rosado.cljs-base "0.1.0"
  :description "A simple base project for ClojureScript experiments"
  :source-paths ["src-clj"]
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [hiccup "1.0.0"]
                 [ring "1.1.6"]
                 [compojure "1.1.3"]]
  :plugins [[lein-cljsbuild "0.2.10"]]
  :cljsbuild {:builds [{:source-paths "src-cljs"
                         :compiler {:output-to "resources/public/js/main.js"
                                    :optimization :simple
                                    :pretty-print true}}]}
  :ring {:handler rosado.cljs-base/app
         :auto-reload? true}
  :repl-options {:init-ns rosado.cljs-base})