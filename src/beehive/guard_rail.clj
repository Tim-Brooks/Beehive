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
           (net.uncontended.precipice.rejected Rejected)
           (beehive.generator EnumBuilder)))

(set! *warn-on-reflection* true)

(def keyword->enum {:max-concurrency-level-exceeded Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED
                    :circuit-open Rejected/CIRCUIT_OPEN})

(defn- do-assertions [clauses]
  (assert (even? (count clauses)))
  (assert (every? keyword? (take-nth 2 clauses)))
  (assert (every? seq? (take-nth 2 (rest clauses)))))

(defn to-enum-string [k]
  (.toUpperCase (.replace (name k) \- \_)))

(defn gen-fn [ks cpath]
  (let [^Class enum (resolve cpath)
        string->enum (into {} (map (fn [^Enum e] [(.name e) e])
                                   (.getEnumConstants enum)))]
    (into {} (mapv (fn [k] [k (symbol (to-enum-string k))]) ks))))

(defmacro backpressure [builder & clauses]
  (do-assertions clauses)
  (let [ks (set (take-nth 2 clauses))
        cpath (EnumBuilder/build (mapv to-enum-string ks))
        cpath (symbol cpath)
        key->enum (gen-fn ks cpath)
        clauses (partition 2 clauses)
        b (gensym)]
    `(let [~b ~builder]
       ~@(map (fn [[reason bp-fn-seq]]
                `(.addBackPressure ~b (~(first bp-fn-seq)
                                         ~@(rest bp-fn-seq)
                                         (. ~cpath ~(get key->enum reason)))))
              clauses)
       ~b)))

(defn- add-backpressure [^GuardRailBuilder builder reason->backpressure]
  (doseq [[_ bp] reason->backpressure]
    (.addBackPressure builder bp))
  builder)

(defn guard-rail
  [name result-metrics rejected-metrics
   & {:keys [latency-metrics reason->backpressure]}]
  (keyword "dfd")
  (with-meta
    {:guard-rail
     (-> (GuardRailBuilder.)
         (.name name)
         (.resultMetrics result-metrics)
         (.rejectedMetrics rejected-metrics)
         (cond->
           latency-metrics
           (.resultLatency latency-metrics)
           reason->backpressure
           (add-backpressure reason->backpressure)))}
    {:enum->keyword (fn [e]
                      (cond
                        (identical? e Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED)
                        :max-concurrency
                        (identical? e Rejected/CIRCUIT_OPEN)
                        :circuit-open))
     :keyword->enum (fn [k]
                      (case k
                        :max-concurrency Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED
                        :circuit-open Rejected/CIRCUIT_OPEN))}))


