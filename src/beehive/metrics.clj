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

(ns beehive.metrics
  (:require [beehive.enums :as enums]
            [beehive.utils :as utils])
  (:import (beehive.enums EmptyEnum)
           (net.uncontended.precipice.metrics CountMetrics
                                              IntervalLatencyMetrics
                                              LatencyMetrics
                                              LatencySnapshot
                                              MetricCounter
                                              NoOpLatencyMetrics
                                              RollingCountMetrics)))

(set! *warn-on-reflection* true)

(defprotocol RollingCountsView
  (count-for-period [this metric duration time-unit]))

(defprotocol CountsView
  (total-count [this metric]))

(defn- get-total-metric-count [^CountMetrics metrics metric key->enum]
  (when-let [metric (get key->enum metric)]
    (.getMetricCount metrics metric)))

(defn- get-metric-count-for-period
  [^RollingCountMetrics metrics metric duration time-unit key->enum]
  (when-let [metric (get key->enum metric)]
    (.getMetricCountForPeriod
      metrics metric duration (utils/->time-unit time-unit))))

(defn no-op-metrics
  ([] (no-op-metrics {}))
  ([key->enum]
   (let [first-enum (first key->enum)
         enum-class (if first-enum (class (val first-enum)) EmptyEnum)
         metrics (MetricCounter/noOpCounter enum-class)]
     (with-meta
       (reify
         CountsView
         (total-count [this metric]
           (get-total-metric-count metrics metric key->enum))
         RollingCountsView
         (count-for-period [this metric duration time-unit]
           (get-metric-count-for-period
             metrics metric duration time-unit key->enum)))
       {:precipice-metrics metrics}))))

(defn count-metrics [key->enum]
  (if-let [first-enum (first key->enum)]
    (let [precipice-metrics (MetricCounter/newCounter (class (val first-enum)))]
      (with-meta
        (reify CountsView
          (total-count [this metric]
            (get-total-metric-count precipice-metrics metric key->enum)))
        {:precipice-metrics precipice-metrics}))
    (no-op-metrics)))

(defn rolling-count-metrics
  ([key->enum] (rolling-count-metrics key->enum (* 60 15) 1 :seconds))
  ([key->enum slots-to-track resolution time-unit]
   (if-let [first-type (first key->enum)]
     (let [precipice-metrics (RollingCountMetrics.
                               (class (val first-type))
                               slots-to-track
                               resolution
                               (utils/->time-unit time-unit))]
       (with-meta
         (reify
           RollingCountsView
           (count-for-period [this metric duration time-unit]
             (get-metric-count-for-period
               precipice-metrics metric duration time-unit key->enum))
           CountsView
           (total-count [this metric]
             (get-total-metric-count precipice-metrics metric key->enum)))
         {:precipice-metrics precipice-metrics}))
     (no-op-metrics))))

(defprotocol LatencyView
  (latency [this metric]))

(defprotocol IntervalLatencyView
  (interval-latency [this metric]))

(defn- snapshot-to-map [^LatencySnapshot snapshot]
  {:latency-50 (.-latency50 snapshot)
   :latency-90 (.-latency90 snapshot)
   :latency-99 (.-latency99 snapshot)
   :latency-99-9 (.-latency999 snapshot)
   :latency-99-99 (.-latency9999 snapshot)
   :latency-99-999 (.-latency99999 snapshot)
   :latency-max (.-latencyMax snapshot)
   :latency-mean (.-latencyMean snapshot)})

(defn- interval-latency-snapshot
  [^IntervalLatencyMetrics latency-metrics metric key->enum]
  (when-let [enum (get key->enum metric)]
    (snapshot-to-map (.intervalSnapshot latency-metrics enum))))

(defn- latency-snapshot
  [^LatencyMetrics latency-metrics metric key->enum]
  (when-let [enum (get key->enum metric)]
    (snapshot-to-map (.latencySnapshot latency-metrics enum))))

(defn- precipice-metrics [key->enum highest-trackable-value significant-digits]
  (if-let [first-type (first key->enum)]
    (IntervalLatencyMetrics.
      (class (val first-type))
      (long highest-trackable-value)
      (long significant-digits))
    (NoOpLatencyMetrics.)))

(defn latency-metrics [key->enum highest-trackable-value significant-digits]
  (let [precipice-metrics (precipice-metrics
                            key->enum highest-trackable-value significant-digits)]
    (with-meta
      (reify
        LatencyView
        (latency [this metric]
          (latency-snapshot precipice-metrics metric key->enum))
        IntervalLatencyView
        (interval-latency [this metric]
          (interval-latency-snapshot precipice-metrics metric key->enum)))
      {:precipice-metrics precipice-metrics})))