(ns rosdo.cljs-base
  (:require [goog.dom :as dom]
            [rosado.arrowlets :as arrows]
            [clojure.browser.event :as events]))

(defn add1 [x] (+ x 1))

;(def add2 (arrows/next>> add1 add1))

;(.log js/console (str "add2: " (add2 1)))

;;; cps

(def add2-cps (-> add1 arrows/lift (arrows/next>> add1)))

(.log js/console (str "add2-cps: " (arrows/run add2-cps 1)))

;;;

(defn simple-event
  [event-name]
  (let [cps (fn [target k]
              (let [handler (fn handler [e]
                              (events/unlisten target event-name handler false)
                              (k e))]
                (events/listen target event-name handler false)))]
    (arrows/Cps. cps)))

(def counter (atom 0))

(defn click-target-arrow
  [e]
  (let [target (.-currentTarget e)]
    (set! (.-textContent target) (format "You clicked me: %s" (swap! counter inc)))
    (.log js/console (str "clicked " @counter))
    target))

;;; handle single click
(do
  (-> "click"
      simple-event
      (arrows/next>> click-target-arrow)
      (arrows/run (dom/getElement "click-count"))))

;;; handle two clicks

(do
  (-> (simple-event "click")
      (arrows/next>> click-target-arrow)
      (arrows/next>> (arrows/next>> (simple-event "click")
                                    click-target-arrow))
      (arrows/run (dom/getElement "two-clicks"))))

;;; or

(do
  (-> (simple-event "click")
      (arrows/next>> click-target-arrow)
      (arrows/next>> (simple-event "click"))
      (arrows/next>> click-target-arrow)
      (arrows/run (dom/getElement "different-two-clicks"))))

;;; opreators
;;; 1. looping with repeat

(defn swap [coll i j] (assoc coll i (coll j) j (coll i)))

(def sort-result (atom nil))

(defn bubble-sort
  [x]
  (let [{:keys [coll i j]} x]
    (cond (< (inc j) i)
          (if (> (coll j) (coll (inc j)))
            (arrows/make-repeat {:coll (swap coll j (inc j)) :i i :j (inc j)})
            (arrows/make-repeat {:coll coll :i i :j (inc j)}))
          
          (< 0 i)
          (arrows/make-repeat {:coll coll :i (dec i) :j 0})
          
          :else
          (do
            (reset! sort-result coll)
            (arrows/make-done coll)))))

(defn async-bubble-sort
  []
  (let [coll [4 1 3 9 2 8]]
    (arrows/run (arrows/repeat-arrow (arrows/lift-async bubble-sort) 0)
                {:coll coll :i (count coll) :j 0})))

(when true
  (async-bubble-sort)
  (js/setTimeout
   #(.log js/console (str "sorted: " @sort-result))
   500))

;;; product arrow

(defn click-two-targets-arrow
  [target1 target2]
  (.log js/console (str "clicked both buttons")))

(let [a1 (arrows/event-arrow "click") a2 (arrows/event-arrow "click")
      prod (arrows/product1 a1 a2)]
  (arrows/run (arrows/next>> prod click-two-targets-arrow)
              [(dom/getElement "two-clicks") (dom/getElement "different-two-clicks")]))

;;; or-arrow

(defn write-arrow
  [s]
  (fn [ev]
    (let [t (.-currentTarget ev)]
      (set! (.-textContent t) s)
      t)))

(def heads (arrows/const-arrow (dom/getElement "heads")))
(def tails (arrows/const-arrow (dom/getElement "tails")))

(arrows/run
 (arrows/or-arrow (-> heads
                      (arrows/next>> (arrows/event-arrow "click"))
                      (arrows/next>> (write-arrow "I win")))
                  (-> tails
                      (arrows/next>> (arrows/event-arrow "click"))
                      (arrows/next>> (write-arrow "You loose!"))))
 nil)

