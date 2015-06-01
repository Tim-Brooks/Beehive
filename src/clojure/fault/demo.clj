(ns fault.demo
  (:require [fault.core :as c]
            [fault.future :as f])
  (:import (java.util Random)))

(defn thing [x]
  (* x x))

(thing 9)

(def t
  (f/rejected-action-future :time-out))

(defonce s (c/service "1" 1 10))

(defn multi-5 [thing]
  (* 8 8))

(defn my-divide [multiply param2]
  (-> {}
      (assoc :hello :k)
      (assoc :no :yes)))

(defn submit []
  (c/submit-action s (fn []
                       (let [{:keys [number]} {:number {:k :r :4 :d}}]
                         (doseq [i (range 100000000000)]
                           (.nextInt (Random.)))
                         {:hello (my-divide number 9)
                          :x (mapv #(* number %) [1 2 3])}))
                   1))
