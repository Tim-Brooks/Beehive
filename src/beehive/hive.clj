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

(ns beehive.hive
  (:require [beehive.enums :as enums])
  (:import (clojure.lang ILookup)))

(set! *warn-on-reflection* true)

(def default-result-type
  {:success true
   :error false
   :timeout false})

(defn return-nil [x] nil)

(deftype Hive
  [^net.uncontended.precipice.GuardRail guard-rail result-metrics rejected-metrics
   latency-metrics backpressure result-enums rejected-enum]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :name (.getName guard-rail)
      :result-metrics result-metrics
      :rejected-metrics rejected-metrics
      :latency-metrics latency-metrics
      :backpressure backpressure
      default))
  Object
  (toString [this]
    (str {:name (.getName guard-rail)
          :result-metrics result-metrics
          :rejected-metrics rejected-metrics
          :latency-metrics latency-metrics
          :backpressure backpressure})))

(defn- do-new-assertions [clauses]
  (assert (every? keyword? (keys clauses)))
  (assert (every? seq? (vals (rest clauses)))))

(defn add-bp [^net.uncontended.precipice.GuardRailBuilder builder reason->backpressure]
  (doseq [backpressure (vals reason->backpressure)]
    (.addBackPressure builder backpressure))
  builder)

(defmacro create-bp [reason->backpressure rejected-key->enum]
  (let [r (gensym)]
    `(let [~r ~rejected-key->enum]
       (-> {}
           ~@(map (fn [[reason bp-fn-seq]]
                    (list assoc reason `(~(first bp-fn-seq)
                                          ~@(rest bp-fn-seq)
                                          (get ~r ~reason))))
                  reason->backpressure)))))

(defmacro beehive
  [name result-metrics rejected-metrics &
   {:keys [latency-metrics backpressure result->success?]}]
  (let [res-metrics-fn (first result-metrics)
        rej-metrics-fn (first rejected-metrics)
        res-metrics-args (rest result-metrics)
        rej-metrics-args (rest rejected-metrics)
        latency-metrics-fn (or (first latency-metrics) return-nil)
        latency-args (rest latency-metrics)
        result->success? (or result->success? default-result-type)]
    `(let [result-key->enum# (enums/result-keys->enum ~result->success?)
           rejected-key->enum# (enums/rejected-keys->enum ~(keys backpressure))
           backpressure# (create-bp ~backpressure rejected-key->enum#)
           result-metrics# (~res-metrics-fn result-key->enum# ~@res-metrics-args)
           rejected-metrics# (~rej-metrics-fn rejected-key->enum# ~@rej-metrics-args)
           latency-metrics# (~latency-metrics-fn result-key->enum# ~@latency-args)
           builder# (-> (net.uncontended.precipice.GuardRailBuilder.)
                        (.name ~name)
                        (.resultMetrics (.metrics ^beehive.metrics.MetricHolder result-metrics#))
                        (.rejectedMetrics (.metrics ^beehive.metrics.MetricHolder rejected-metrics#))
                        (cond->
                          latency-metrics#
                          (.resultLatency (.metrics ^beehive.metrics.MetricHolder latency-metrics#))
                          backpressure#
                          (add-bp backpressure#)))]
       (Hive.
         (.build ^net.uncontended.precipice.GuardRailBuilder builder#)
         result-metrics#
         rejected-metrics#
         latency-metrics#
         backpressure#
         result-key->enum#
         rejected-key->enum#))))