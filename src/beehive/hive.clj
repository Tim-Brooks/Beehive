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
           (net.uncontended.precipice GuardRail)))

(set! *warn-on-reflection* true)

(def default-result-type
  {:success true
   :error false
   :timeout false})

(defn- return-nil [x] nil)

(deftype Hive
  [^net.uncontended.precipice.GuardRail guard-rail result-metrics rejected-metrics
   latency-metrics back-pressure result-enums rejected-enums]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :name (.getName guard-rail)
      :result-metrics result-metrics
      :rejected-metrics rejected-metrics
      :latency-metrics latency-metrics
      :back-pressure back-pressure
      default))
  Object
  (toString [this]
    (str {:name (.getName guard-rail)
          :result-metrics result-metrics
          :rejected-metrics rejected-metrics
          :latency-metrics latency-metrics
          :back-pressure back-pressure})))

(defn add-bp [^net.uncontended.precipice.GuardRailBuilder builder reason->back-pressure]
  (doseq [back-pressure (vals reason->back-pressure)]
    (.addBackPressure builder back-pressure))
  builder)

(defmacro create-bp [reason->back-pressure rejected-key->enum]
  (let [r (gensym)]
    `(let [~r ~rejected-key->enum]
       (-> {}
           ~@(map (fn [[reason bp-fn-seq]]
                    (list assoc reason `(~(first bp-fn-seq)
                                          ~@(rest bp-fn-seq)
                                          (get ~r ~reason))))
                  reason->back-pressure)))))

(defmacro beehive
  [name result-metrics rejected-metrics &
   {:keys [latency-metrics back-pressure result->success?]}]
  (let [res-metrics-fn (first result-metrics)
        rej-metrics-fn (first rejected-metrics)
        res-metrics-args (rest result-metrics)
        rej-metrics-args (rest rejected-metrics)
        latency-metrics-fn (or (first latency-metrics) return-nil)
        latency-args (rest latency-metrics)
        result->success? (or result->success? default-result-type)]
    `(let [result-key->enum# (enums/result-keys->enum ~result->success?)
           rejected-key->enum# (enums/rejected-keys->enum ~(keys back-pressure))
           back-pressure# (create-bp ~back-pressure rejected-key->enum#)
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
                          back-pressure#
                          (add-bp back-pressure#)))]
       (Hive.
         (.build ^net.uncontended.precipice.GuardRailBuilder builder#)
         result-metrics#
         rejected-metrics#
         latency-metrics#
         back-pressure#
         result-key->enum#
         rejected-key->enum#))))

(defn release
  ([^Hive beehive {:keys [permit-count]}]
   (when permit-count
     (let [^GuardRail guard-rail (.-guard_rail beehive)
           end-nanos (.nanoTime (.getClock guard-rail))]
       (.releasePermitsWithoutResult guard-rail permit-count end-nanos))))
  ([^Hive beehive {:keys [permit-count start-nanos]} result]
   (when permit-count
     (let [^GuardRail guard-rail (.-guard_rail beehive)
           result-enum (get (.-result_enums beehive) result)
           end-nanos (.nanoTime (.getClock guard-rail))]
       (.releasePermits
         guard-rail result-enum permit-count start-nanos end-nanos)))))

(defn acquire [^Hive beehive permits]
  (let [^GuardRail guard-rail (.-guard_rail beehive)
        start-nanos (.nanoTime (.getClock guard-rail))]
    (if-let [rejected-reason (.acquirePermits guard-rail permits start-nanos)]
      {:rejected? true :reason (get (.rejected_enums beehive) rejected-reason)}
      {:start-nanos start-nanos :permit-count permits})))