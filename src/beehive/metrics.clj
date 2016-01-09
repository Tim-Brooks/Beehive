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
  (:import (net.uncontended.precipice.metrics DefaultActionMetrics Metric)))

(set! *warn-on-reflection* true)

(defn get-count [metrics ^Metric metric]
  (.getMetricCountForTotalPeriod metrics metric))

(defn get-count-for-period [metrics metric duration time-unit]
  (.getMetricCountForTimePeriod
    metrics metric duration (utils/->time-unit time-unit)))

(defn default-metrics
  ([{:keys [slots-to-track resolution time-unit]}]
    (default-metrics slots-to-track resolution time-unit))
  ([slots-to-track resolution time-unit]
   (DefaultActionMetrics. slots-to-track
                          resolution
                          (utils/->time-unit time-unit))))