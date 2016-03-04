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
  (:refer-clojure :exclude [promise])
  (:require [beehive.enums :as enums]
            [beehive.future :as f]
            [beehive.metrics :as metrics])
  (:import (clojure.lang APersistentMap)
           (net.uncontended.precipice GuardRail GuardRailBuilder)
           (beehive.metrics MetricHolder)
           (net.uncontended.precipice.factories Asynchronous Synchronous)
           (net.uncontended.precipice.rejected RejectedException)
           (net.uncontended.precipice.concurrent PrecipicePromise)))

(set! *warn-on-reflection* true)

(defprotocol Hive
  (beehive-name [this])
  (result-metrics [this])
  (rejected-metrics [this])
  (latency-metrics [this])
  (back-pressure [this]))

(extend-protocol Hive
  APersistentMap
  (beehive-name [this] (:name this))
  (result-metrics [this] (:result-metrics this))
  (rejected-metrics [this] (:rejected-metrics this))
  (latency-metrics [this] (:latency-metrics this))
  (back-pressure [this] (:back-pressure this)))


(defn add-bp [^GuardRailBuilder builder mechanisms]
  (doseq [back-pressure mechanisms]
    (.addBackPressure builder back-pressure))
  builder)

(defmacro create-back-pressure [rejected-keys metrics & mechanisms]
  (let [{:keys [key->enum-string cpath]} (enums/generate-rejected-enum rejected-keys)
        key->form (into {} (map (fn [[k s]]
                                  [k (enums/enum-form cpath s)])
                                key->enum-string))
        metrics-fn (first metrics)
        metric-fn-args (rest metrics)]
    `{:rejected-key->enum ~key->form
      :rejected-metrics (~metrics-fn ~key->form ~@metric-fn-args)
      :back-pressure [~@(for [form mechanisms]
                          (clojure.walk/postwalk-replace key->form form))]}))

(defmacro results
  [result->success? metrics-seq & latency-metrics-seq]
  (let [{:keys [key->enum-string cpath]} (enums/generate-result-enum
                                           result->success?)
        key->form (into {} (map (fn [[k s]]
                                  [k (enums/enum-form cpath s)])
                                key->enum-string))
        metrics-fn (first metrics-seq)
        metric-fn-args (rest metrics-seq)
        latency-metrics-seq (first latency-metrics-seq)
        latency-metrics-fn (or (first latency-metrics-seq) identity)
        latency-metrics-args (rest latency-metrics-seq)]
    `(cond-> {:result-key->enum ~key->form
              :result-metrics (~metrics-fn ~key->form ~@metric-fn-args)}
             ~latency-metrics-seq
             (assoc :latency-metrics (~latency-metrics-fn
                                       ~key->form
                                       ~@latency-metrics-args)))))

(defn beehive
  [name
   {:keys [result-key->enum result-metrics latency-metrics]}
   {:keys [rejected-key->enum rejected-metrics back-pressure]}]
  (let [builder (-> (GuardRailBuilder.)
                    (.name name)
                    (.resultMetrics (.metrics ^MetricHolder result-metrics))
                    (.rejectedMetrics (.metrics ^MetricHolder rejected-metrics))
                    (cond->
                      latency-metrics
                      (.resultLatency (.metrics ^MetricHolder latency-metrics))
                      back-pressure
                      (add-bp back-pressure)))]
    (cond-> {:result-metrics result-metrics
             :rejected-metrics rejected-metrics
             :result-key->enum result-key->enum
             :rejected-key->enum rejected-key->enum
             :guard-rail (.build ^GuardRailBuilder builder)}
            latency-metrics
            (assoc :latency-metrics latency-metrics)
            back-pressure
            (assoc :back-pressure back-pressure))))

(defn promise
  [{:keys [guard-rail rejected-metrics]} permits]
  (try
    (let [^PrecipicePromise java-p (Asynchronous/acquirePermitsAndPromise
                                     guard-rail permits)]
      )
    (catch RejectedException e
      {:rejected? true :reason (get rejected-metrics (.reason e))})))

(defn completable
  [{:keys [guard-rail rejected-metrics]} permits]
  (try
    (Synchronous/acquirePermitsAndCompletable guard-rail permits)
    (catch RejectedException e
      {:rejected? true :reason (get rejected-metrics (.reason e))})))

(defn release
  ([beehive {:keys [permit-count]}]
   (when permit-count
     (let [^GuardRail guard-rail (:guard-rail beehive)
           end-nanos (.nanoTime (.getClock guard-rail))]
       (.releasePermitsWithoutResult guard-rail permit-count end-nanos))))
  ([beehive {:keys [permit-count start-nanos]} result]
   (when permit-count
     (let [^GuardRail guard-rail (:guard-rail beehive)
           result-enum (get (:result-key->enum beehive) result)
           end-nanos (.nanoTime (.getClock guard-rail))]
       (.releasePermits
         guard-rail result-enum permit-count start-nanos end-nanos)))))

(defn acquire [beehive permits]
  (let [^GuardRail guard-rail (:guard-rail beehive)
        start-nanos (.nanoTime (.getClock guard-rail))]
    (if-let [rejected-reason (.acquirePermits guard-rail permits start-nanos)]
      {:rejected? true :reason (get (:rejected-key->enum beehive) rejected-reason)}
      {:start-nanos start-nanos :permit-count permits})))