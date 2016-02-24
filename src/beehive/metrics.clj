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
  (:require [beehive.compatibility :as c]
            [beehive.utils :as utils])
  (:import (net.uncontended.precipice.metrics CountMetrics
                                              RollingCountMetrics
                                              IntervalLatencyMetrics
                                              LatencyMetrics LatencySnapshot)
           (net.uncontended.precipice.result TimeoutableResult)))

(set! *warn-on-reflection* true)

(defn total-count [metrics metric]
  (when-let [metric (or (c/clj-result->result metric)
                        (c/clj-rejected->rejected metric))]
    (.getMetricCount ^CountMetrics metrics metric)))

(defn count-for-period [metrics metric duration time-unit]
  (when-let [metric (or (c/clj-result->result metric)
                        (c/clj-rejected->rejected metric))]
    (.getMetricCountForPeriod
      ^RollingCountMetrics metrics metric duration (utils/->time-unit time-unit))))

(defn count-metrics
  ([] (RollingCountMetrics. TimeoutableResult))
  ([{:keys [slots-to-track resolution time-unit]}]
   (count-metrics slots-to-track resolution time-unit))
  ([slots-to-track resolution time-unit]
   (RollingCountMetrics. TimeoutableResult
                         slots-to-track
                         resolution
                         (utils/->time-unit time-unit))))

(defn interval-latency-snapshot [latency-metrics metric]
  (when-let [metric (c/clj-result->result metric)]
    (let [snapshot (.intervalSnapshot ^IntervalLatencyMetrics latency-metrics metric)]
      {:latency-50 (.-latency50 snapshot)
       :latency-90 (.-latency90 snapshot)
       :latency-99 (.-latency99 snapshot)
       :latency-99-9 (.-latency999 snapshot)
       :latency-99-99 (.-latency9999 snapshot)
       :latency-99-999 (.-latency99999 snapshot)
       :latency-max (.-latencyMax snapshot)
       :latency-mean (.-latencyMean snapshot)})))

(defn latency-snapshot [latency-metrics metric]
  (when-let [metric (c/clj-result->result metric)]
    (let [snapshot (.latencySnapshot ^LatencyMetrics latency-metrics metric)]
      {:latency-50 (.-latency50 snapshot)
       :latency-90 (.-latency90 snapshot)
       :latency-99 (.-latency99 snapshot)
       :latency-99-9 (.-latency999 snapshot)
       :latency-99-99 (.-latency9999 snapshot)
       :latency-99-999 (.-latency99999 snapshot)
       :latency-max (.-latencyMax snapshot)
       :latency-mean (.-latencyMean snapshot)})))

(defn latency-metrics [highest-trackable-value significant-digits]
  (IntervalLatencyMetrics. TimeoutableResult
                           (long highest-trackable-value)
                           (long significant-digits)))