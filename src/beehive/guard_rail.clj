;; Copyright 2016 Timothy Brooks
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

(ns beehive.guard-rail
  (:import (net.uncontended.precipice GuardRailBuilder)
           (net.uncontended.precipice.rejected Rejected)))

(set! *warn-on-reflection* true)

(def keyword->enum {:max-concurrency-level-exceeded Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED
                    :circuit-open Rejected/CIRCUIT_OPEN})

(defn- do-assertions [clauses]
  (assert (even? (count clauses)))
  (assert (every? keyword? (take-nth 2 clauses)))
  (assert (every? seq? (take-nth 2 (rest clauses)))))

(defmacro backpressure [builder & clauses]
  (do-assertions clauses)
  (let [clauses (partition 2 clauses)]
    (let [gx (gensym)]
      `(let [~gx ~builder]
         ~@(map (fn [[reason bp-fn-seq]]
                  `(.addBackPressure ~gx (~(first bp-fn-seq)
                                           ~@(rest bp-fn-seq)
                                           (get keyword->enum ~reason))))
                clauses)
         ~gx))))

(defn- add-backpressure [^GuardRailBuilder builder reason->backpressure]
  (doseq [[_ bp] reason->backpressure]
    (.addBackPressure builder bp))
  builder)

(defn guard-rail
  [name result-metrics rejected-metrics
   & {:keys [latency-metrics reason->backpressure]}]
  (-> (GuardRailBuilder.)
      (.name name)
      (.resultMetrics result-metrics)
      (.rejectedMetrics rejected-metrics)
      (cond->
        latency-metrics
        (.resultLatency latency-metrics)
        reason->backpressure
        (add-backpressure reason->backpressure))))


