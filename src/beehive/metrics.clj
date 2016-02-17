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
  (:import (net.uncontended.precipice.metrics TotalCountMetrics
                                              RollingCountMetrics
                                              IntervalLatencyMetrics)
           (net.uncontended.precipice TimeoutableResult)))

(set! *warn-on-reflection* true)

(defn total-count [metrics metric]
  (when-let [metric (c/clj-result->result metric)]
    (.getTotalMetricCount ^TotalCountMetrics metrics metric)))

(defn count-for-period [metrics metric duration time-unit]
  (when-let [metric (c/clj-result->result metric)]
    (.getMetricCount
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

(defn latency-metrics [highest-trackable-value significant-digits]
  (IntervalLatencyMetrics. TimeoutableResult
                           (long highest-trackable-value)
                           (long significant-digits)))