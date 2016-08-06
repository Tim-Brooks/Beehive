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
  (:import (beehive.java EmptyEnum ToCLJ)
           (net.uncontended.precipice.metrics Rolling)
           (net.uncontended.precipice.metrics.counts PartitionedCount
                                                     NoOpCounter
                                                     TotalCounts
                                                     RollingCounts)
           (net.uncontended.precipice.metrics.latency TotalLatency
                                                      AtomicHistogram)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

(defprotocol CountsView
  (get-count [this metric]))

(defn- get-metric-count [^PartitionedCount counter metric key->enum]
  (when-let [metric (get key->enum metric)]
    (.getCount counter metric)))

(defn no-op-metrics
  ([] (no-op-metrics EmptyEnum))
  ([^Class enum-class]
   (let [key->enum (into {} (map (fn [^ToCLJ e] [(.keyword e) e])
                                 (.getEnumConstants enum-class)))
         metrics (TotalCounts. (NoOpCounter. enum-class))]
     (with-meta
       (reify
         CountsView
         (get-count [this metric]
           (get-metric-count metrics metric key->enum)))
       {:precipice-metrics metrics}))))

(defn count-metrics [^Class enum-class]
  (if-not (identical? enum-class EmptyEnum)
    (let [key->enum (into {} (map (fn [^ToCLJ e] [(.keyword e) e])
                                  (.getEnumConstants enum-class)))
          precipice-metrics (TotalCounts. enum-class)]
      (with-meta
        (reify CountsView
          (get-count [this metric]
            (get-metric-count precipice-metrics metric key->enum)))
        {:precipice-metrics precipice-metrics}))
    (no-op-metrics)))

(defn rolling-count-metrics
  ([enum-class] (rolling-count-metrics enum-class (* 60 15) 1 :seconds))
  ([^Class enum-class  slots-to-track resolution time-unit]
   (if-not (identical? enum-class EmptyEnum)
     (let [key->enum (into {} (map (fn [^ToCLJ e] [(.keyword e) e])
                                   (.getEnumConstants enum-class)))
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
     (no-op-metrics))))

(defprotocol LatencyView
  (latency [this metric]))

(defn- latency-snapshot
  [latency-metrics metric key->enum]
  (when-let [enum (get key->enum metric)]
    nil))

(defn- precipice-metrics [key->enum highest-trackable-value significant-digits]
  (let [first-type (first key->enum)]
    (TotalLatency.
      (AtomicHistogram.
        (class (val first-type)) highest-trackable-value significant-digits))))

(defn latency-metrics [key->enum highest-trackable-value significant-digits]
  (let [precipice-metrics (precipice-metrics
                            key->enum highest-trackable-value significant-digits)]
    (with-meta
      (reify
        LatencyView
        (latency [this metric]
          (latency-snapshot precipice-metrics metric key->enum)))
      {:precipice-metrics precipice-metrics})))