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
  (:require [beehive.utils :as utils]
            [beehive.enums :as enums])
  (:import (beehive.java EmptyEnum)
           (net.uncontended.precipice.metrics.counts PartitionedCount
                                                     NoOpCounter
                                                     TotalCounts
                                                     RollingCounts)
           (net.uncontended.precipice.metrics.latency TotalLatency
                                                      NoOpLatency
                                                      ConcurrentHistogram
                                                      PartitionedLatency)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

(defprotocol CountsView
  (get-count [this metric]))

(defn- get-metric-count [^PartitionedCount counter metric key->enum]
  (when-let [metric (get key->enum metric)]
    (.getCount counter metric)))

(defn no-op-counts
  ([] (no-op-counts EmptyEnum))
  ([^Class enum-class]
   (let [key->enum (enums/enum-class-to-keyword->enum enum-class)
         metrics (TotalCounts. (NoOpCounter. enum-class))]
     (with-meta
       (reify
         CountsView
         (get-count [this metric]
           (get-metric-count metrics metric key->enum)))
       {:precipice-metrics metrics}))))

(defn count-metrics [^Class enum-class]
  (if-not (identical? enum-class EmptyEnum)
    (let [key->enum (enums/enum-class-to-keyword->enum enum-class)
          precipice-metrics (TotalCounts. enum-class)]
      (with-meta
        (reify CountsView
          (get-count [this metric]
            (get-metric-count precipice-metrics metric key->enum)))
        {:precipice-metrics precipice-metrics}))
    (no-op-counts)))

(defn rolling-count-metrics
  ([enum-class] (rolling-count-metrics enum-class (* 60 15) 1 :seconds))
  ([^Class enum-class slots-to-track resolution time-unit]
   (if-not (identical? enum-class EmptyEnum)
     (let [key->enum (enums/enum-class-to-keyword->enum enum-class)
           precipice-metrics
           (RollingCounts.
             enum-class
             (int slots-to-track)
             (.toNanos ^TimeUnit (utils/->time-unit time-unit) (long resolution)))]
       (with-meta
         (reify
           CountsView
           (get-count [this metric]
             (get-metric-count precipice-metrics metric key->enum)))
         {:precipice-metrics precipice-metrics}))
     (no-op-counts))))

(defprotocol LatencyView
  (get-latency [this metric percentile]))

(defn- latency-at-percentile
  [^PartitionedLatency latency-metrics metric percentile key->enum]
  (when-let [enum (get key->enum metric)]
    (.getValueAtPercentile latency-metrics enum percentile)))

(defn- precipice-metrics [enum-class]
  (TotalLatency. (ConcurrentHistogram. enum-class)))

(defn- decorate-latency [key->enum precipice-metrics]
  (with-meta
    (reify
      LatencyView
      (get-latency [this metric percentile]
        (latency-at-percentile precipice-metrics metric percentile key->enum)))
    {:precipice-metrics precipice-metrics}))

(defn no-op-latency-metrics
  ([] (no-op-latency-metrics EmptyEnum))
  ([^Class enum-class]
   (let [key->enum (enums/enum-class-to-keyword->enum enum-class)
         precipice-metrics (TotalLatency. (NoOpLatency. enum-class))]
     (decorate-latency key->enum precipice-metrics))))

(defn latency-metrics [enum-class]
  (if-not (identical? enum-class EmptyEnum)
    (let [key->enum (enums/enum-class-to-keyword->enum enum-class)
          precipice-metrics (precipice-metrics enum-class)]
      (decorate-latency key->enum precipice-metrics))
    (no-op-latency-metrics)))