(ns rosado.arrowlets
  (:require [clojure.browser.event :as events])
  ;; (:use [cljs.core :only [Fn]])
  ;;(:require [cljs.core :as c])
  )

(defn log [msg] (.log js/console msg))

;;; ========== protocols 

(defprotocol Arrow
  (-arrow [f])
  (-next>> [f g]))

(defprotocol RunnableArrow
  (-run [o x])
  (-run [o x p]))

(defprotocol ToCpsArrow
  (-lift [o]))

(defprotocol ToAsyncArrow
  (-lift-async [o]))

;;; ========== impls

(extend-type function
  Arrow
  (-arrow [this]
    this)
  (-next>> [this g]
    (let [g (-arrow g)]
      (fn [x] (-> x this g)))))

;;; ========== user facing fns

(defn arrow
  [func]
  (-arrow func))

(defn next>>
  [f g]
  (-next>> f g))

(defn lift
  [f]
  (-lift f))

(defn lift-async
  [f]
  (-lift-async f))

(defn run
  ([f x] (-run f x))
  ([f x p] (-run f x p)))

;;; ========== CPS

(deftype Cps [cps]
  Arrow
  (-arrow [this]
    this)
  (-next>> [this g]
    (let [g (lift g)]
      (Cps. (fn [x k]
              (cps x (fn [y]
                       (let [g-cps (.-cps g)]
                         (g-cps y k))))))))
  ToCpsArrow
  (-lift [this]
    this)
  
  RunnableArrow
  (-run [this x]
    (cps x identity))
  (-run [this x _]
    (cps x identity)))

(extend-type function
  ToCpsArrow
  (-lift [func]
    (Cps. (fn [x k] (-> x func k)))))

;;; ========== async arrow

(declare progress-arrow
         add-canceller
         -advance
         -cancel)

(deftype Async [cps]
  Arrow
  (-arrow [this] this)
  (-next>> [this g]
    (let [g (-lift-async g)
          next-cps (fn [x p k]
                     (cps x p (fn [y q]
                                (let [g-cps (.-cps g)]
                                  (g-cps y q k)))))]
      (Async. next-cps)))

  RunnableArrow
  (-run [this x]
    (-run this x nil))
  (-run [this x p]
    (let [p (or p (progress-arrow))]
      (cps x p identity)
      p))
  
  ToAsyncArrow
  (-lift-async [this] this))

(extend-type function
  ToAsyncArrow
  (-lift-async [func]
    (Async. (fn [x p k] (-> x func (k p))))))

(defn const-arrow
  [x]
  (-lift-async (fn [] x)))

(defn event-arrow
  [event-name]
  (Async. (fn [target p k]
            (letfn [(cancel [] (events/unlisten target event-name handler false))
                    (handler [e]
                      (-advance p cancel)
                      (cancel)
                      (k e p))]
              (add-canceller p cancel)
              (events/listen target event-name handler false)))))

(defn add-canceller
  [p cc]
  (let [state (.-prog-state p)]
    (swap! state update-in [:cancels] conj cc)))

(defn add-observer
  [p obs]
  (let [state (.-prog-state p)]
    (swap! state update-in [:observers] conj obs)))

(defn progress-arrow
  []
  (let [state (atom {:cancels [] :observers []})
        cps (Async. (fn [x p k]
                      (swap! state update-in [:observers] conj (fn [y] (k y p)))))]
    (set! (.-prog-state cps) state)
    cps))

(defn -advance
  [p cancel-fn]
  (let [state (.-prog-state p)
        cancels (:cancels @state)
        observers (:observers @state)]
    (swap! state assoc
           :observers []
           :cancels
           (reduce (fn [r c] (if (= c cancel-fn) r (conj r c))) [] cancels))
    (doseq [observer observers] (observer))))

(defn -cancel
  [p]
  (let [state (.-prog-state p)
        snapshot @state]
    (swap! state assoc :cancels [])
    (doseq [c (:cancels snapshot)] (c))))

;;; ========== combinators

(defn make-repeat [x] {:repeat? true :value x})
(defn make-done [x] {:done? true :value x})

(defn repeat-arrow
  "`repeat` arrow combinator.

Creates an arrow which executes repeatedly by yielding to the
execution thread via setTimeout() with given interval. Arrows passed
as argument should use result of #'make-repeat and #'make-done as
return value and accept it as argument. Map returned by #'make-done
and #'make-repeat hold the actual value under :value.

See the bubble sort example in rosado.cljs-base namespace."
  [o interval]
  (Async.
   (fn rep [x p k]
     (let [cps (.-cps (lift-async o))]
       (cps x p (fn [y q]
                  (cond
                   (:repeat? y)
                   (let [tid (atom nil)
                         cancel-fn (fn [] (when-let [tid @tid] (js/clearTimeout tid)))]
                     (add-canceller q cancel-fn)
                     (reset! tid (js/setTimeout (fn []
                                                  (-advance q cancel-fn)
                                                  (rep (:value y) q k))
                                                interval)))
                   
                   (:done? y)
                   (k (:value y) q)

                   :default
                   (throw (js/Error. "repeat? or done?")))))))))

(defn product
  "`product` combinator for Async arrow.

The result is a two element vector."
  [this other]
  (let [other (lift-async other)]
    (Async. (fn [x p k]
              (let [out1 (atom nil) out2 (atom nil) c (atom 2)
                    barrier (fn [] (when (zero? (swap! c dec))
                                    (k [@out1 @out2] p)))]
                (-> this
                    (next>> (fn [y1]
                              (reset! out1 y1)
                              (barrier)))
                    (run (first x) p))
                (-> other
                    (next>> (fn [y2]
                              (reset! out2 y2)
                              (barrier)))
                    (run (second x) p)))))))

(defn or-arrow
  "`or` combinator for Async arrws.

Given two arrows, calling #'run on `or`-ed arrow will execute a1 or
  a2, whichever is triggered first. The other arrow is cancelled."

  [a1 a2]
  (let [a2 (lift-async a2)]
    (Async. (fn [x p k]
              (let [p1 (atom (progress-arrow))
                    p2 (atom (progress-arrow))]
                ;; if one advances, cancell the other
                (run (next>> @p1 (fn [_]
                                  (-cancel @p2)
                                  (reset! p2 nil)))
                     nil)
                (run (next>> @p2 (fn [_]
                                  (-cancel @p1)
                                  (reset! p1 nil)))
                     nil)
                (let [cc (fn []
                           (when @p1 (-cancel @p1))
                           (when @p2 (-cancel @p2)))
                      join-cb (fn [y q]
                                (-advance p cc)
                                (k y q))]
                  (add-canceller p cc)
                  ((.-cps a1) x @p1 join-cb)
                  ((.-cps a2) x @p2 join-cb)))))))

;;; two utility arrows

(defn id-arrow
  []
  (lift-async (fn [f] f)))

(defn dup-arrow
  []
  (lift-async (fn [f] [f f])))

(defn bind-arrow
  "`bind` combinator for Async arrows.

First arrow becomes a trigger for the second arrrow. Result is a
two element vector (like in #'product-arrow)."
  [a1 a2]
  (let [a2 (lift-async a2)]
    (-> (next>> (dup-arrow)
                (product (id-arrow) a1))
        (next>> a2))))
