;; Copyright 2014 Timothy Brooks
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns beehive.demo
  (:require [beehive.core :as c]
            [beehive.future :as f])
  (:import (java.util Random)))

(defn thing [x]
  (* x x))

(thing 9)

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
