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
  (:import (clojure.lang ILookup)
           (net.uncontended.precipice GuardRail GuardRailBuilder)))

(def default-rejected-type
  {:cpath net.uncontended.precipice.rejected.Unrejectable
   :key->enum-string {}})

(def default-result-type
  {:cpath net.uncontended.precipice.result.TimeoutableResult
   :key->enum-string {:success 'SUCCESS
                      :error 'ERROR
                      :timeout 'TIMEOUT}})

(deftype Hive [^GuardRail guard-rail backpressure result-enums rejected-enum]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :name (.getName guard-rail)
      :result-metrics (.getResultMetrics guard-rail)
      :rejected-metrics (.getRejectedMetrics guard-rail)
      :latency-metrics (.getLatencyMetrics guard-rail)
      :backpressure backpressure
      default)))

(defn- do-new-assertions [clauses]
  (assert (every? keyword? (keys clauses)))
  (assert (every? seq? (vals (rest clauses)))))

(defmacro add-bp [builder reason->backpressure {:keys [cpath key->enum-string]}]
  (do-new-assertions reason->backpressure)
  (let [b (gensym)]
    `(let [~b ~builder]
       ~@(map (fn [[reason bp-fn-seq]]
                `(.addBackPressure ~b (~(first bp-fn-seq)
                                        ~@(rest bp-fn-seq)
                                        (. ~cpath ~(get key->enum-string reason)))))
              reason->backpressure)
       ~b)))

(defmacro create-type-map [{:keys [key->enum-string cpath]}]
  `(do
     (-> {}
         ~@(map (fn [[k es]]
                  (list assoc k `(. ~cpath ~es)))
                key->enum-string))))

(defmacro hive
  [name result-metrics rejected-metrics &
   {:keys [latency-metrics backpressure result->success?]}]
  (let [res-metrics-fn (first result-metrics)
        rej-metrics-fn (first rejected-metrics)
        res-metrics-args (rest result-metrics)
        rej-metrics-args (rest rejected-metrics)
        result-type (if result->success?
                      (enums/generate-result-enum result->success?)
                      default-result-type)
        rejected-type (if backpressure
                        (enums/generate-rejected-enum (keys backpressure))
                        default-rejected-type)
        latency-metrics-fn (first latency-metrics)
        latency-args (rest latency-metrics)]
    `(->Hive
       (-> (GuardRailBuilder.)
           (.name ~name)
           (.resultMetrics
             (~res-metrics-fn ~(:cpath result-type) ~@res-metrics-args))
           (.rejectedMetrics
             (~rej-metrics-fn ~(:cpath rejected-type) ~@rej-metrics-args))
           (cond->
             ~latency-metrics
             (.resultLatency
               (~latency-metrics-fn ~(:cpath result-type) ~@latency-args))
             ~backpressure
             (add-bp ~backpressure ~rejected-type)))
       ~backpressure
       (create-type-map ~result-type)
       (create-type-map ~rejected-type))))