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
      default))
  Object
  (toString [this]
    (str {:name (.getName guard-rail)
          :result-metrics (.getResultMetrics guard-rail)
          :rejected-metrics (.getRejectedMetrics guard-rail)
          :latency-metrics (.getLatencyMetrics guard-rail)
          :backpressure backpressure})))

(defn- do-new-assertions [clauses]
  (assert (every? keyword? (keys clauses)))
  (assert (every? seq? (vals (rest clauses)))))

(defn add-bp [builder reason->backpressure]
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

(defmacro create-type-map [{:keys [key->enum-string cpath]}]
  `(do
     (-> {}
         ~@(map (fn [[k es]]
                  (list assoc k `(. ~cpath ~es)))
                key->enum-string))))

(defmacro beehive
  [name result-metrics rejected-metrics &
   {:keys [latency-metrics backpressure result->success?]}]
  (let [res-metrics-fn (first result-metrics)
        rej-metrics-fn (first rejected-metrics)
        res-metrics-args (rest result-metrics)
        rej-metrics-args (rest rejected-metrics)
        latency-metrics-fn (first latency-metrics)
        latency-args (rest latency-metrics)]
    `(let [result-key->enum# (enums/result-keys->enum ~result->success?)
           rejected-key->enum# (enums/rejected-keys->enum ~(keys backpressure))
           backpressure# (create-bp ~backpressure rejected-key->enum#)]
       (->Hive
         (-> (GuardRailBuilder.)
             (.name ~name)
             (.resultMetrics
               (.metrics
                 (~res-metrics-fn result-key->enum# ~@res-metrics-args)))
             (.rejectedMetrics
               (.metrics
                 (~rej-metrics-fn rejected-key->enum# ~@rej-metrics-args)))
             (cond->
               ~latency-metrics
               (.resultLatency
                 (.metrics
                   (~latency-metrics-fn result-key->enum# ~@latency-args)))
               backpressure#
               (add-bp backpressure#))
             (.build))
         backpressure#
         result-key->enum#
         rejected-key->enum#))))

(beehive
  "k"
  (beehive.metrics/count-metrics 15 1 :minutes)
  (beehive.metrics/count-metrics)
  :latency-metrics (beehive.metrics/latency-metrics 10 2)
  :backpressure {:luck (beehive.semaphore/semaphore 2)}
  :result->success? {:success true :failure false})