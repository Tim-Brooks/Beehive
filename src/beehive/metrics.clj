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
  (:require [beehive.utils :as utils])
  (:import (beehive.enums EmptyEnum)
           (net.uncontended.precipice.metrics CountMetrics
                                              RollingCountMetrics
                                              IntervalLatencyMetrics
                                              MetricCounter
                                              NoOpLatencyMetrics)))

(set! *warn-on-reflection* true)

(deftype BeehiveMetrics [metrics keyToEnum])

(defn total-count [^BeehiveMetrics metrics metric]
  (when-let [enum (get (.keyToEnum metrics) metric)]
    (.getMetricCount ^CountMetrics (.metrics metrics) enum)))

(defn count-for-period [^BeehiveMetrics metrics metric duration time-unit]
  (when-let [enum (get (.keyToEnum metrics) metric)]
    (.getMetricCountForPeriod
      ^RollingCountMetrics
      (.metrics metrics)
      enum
      duration
      (utils/->time-unit time-unit))))

(defn no-op-metrics []
  (->BeehiveMetrics (MetricCounter/noOpCounter EmptyEnum) {}))

(defn count-metrics [key->result]
  (if-let [first-type (first key->result)]
    (->BeehiveMetrics
      (MetricCounter/newCounter (class (val first-type))) key->result)
    (no-op-metrics)))

(defn rolling-count-metrics
  ([key->result] (rolling-count-metrics key->result (* 60 15) 1 :seconds))
  ([key->result slots-to-track resolution time-unit]
   (if-let [first-type (first key->result)]
     (->BeehiveMetrics
       (RollingCountMetrics.
         (class (val first-type))
         slots-to-track
         resolution
         (utils/->time-unit time-unit))
       key->result)
     (no-op-metrics))))

(defn interval-latency-snapshot [^BeehiveMetrics latency-metrics metric]
  (when-let [enum (get (.keyToEnum latency-metrics) metric)]
    (let [snapshot (.intervalSnapshot ^IntervalLatencyMetrics
                                      (.-metrics latency-metrics) enum)]
      {:latency-50 (.-latency50 snapshot)
       :latency-90 (.-latency90 snapshot)
       :latency-99 (.-latency99 snapshot)
       :latency-99-9 (.-latency999 snapshot)
       :latency-99-99 (.-latency9999 snapshot)
       :latency-99-999 (.-latency99999 snapshot)
       :latency-max (.-latencyMax snapshot)
       :latency-mean (.-latencyMean snapshot)})))

(defn latency-snapshot [^BeehiveMetrics latency-metrics metric]
  (when-let [enum (get (.keyToEnum latency-metrics) metric)]
    (let [snapshot (.latencySnapshot ^IntervalLatencyMetrics
                                     (.-metrics latency-metrics) enum)]
      {:latency-50 (.-latency50 snapshot)
       :latency-90 (.-latency90 snapshot)
       :latency-99 (.-latency99 snapshot)
       :latency-99-9 (.-latency999 snapshot)
       :latency-99-99 (.-latency9999 snapshot)
       :latency-99-999 (.-latency99999 snapshot)
       :latency-max (.-latencyMax snapshot)
       :latency-mean (.-latencyMean snapshot)})))

(defn latency-metrics [key->result highest-trackable-value significant-digits]
  (if-let [first-type (first key->result)]
    (->BeehiveMetrics
      (IntervalLatencyMetrics.
        (class (val first-type))
        (long highest-trackable-value)
        (long significant-digits))
      key->result)
    (->BeehiveMetrics (NoOpLatencyMetrics.) {})))