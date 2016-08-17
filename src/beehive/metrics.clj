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
  (:import (beehive.java EmptyEnum ToCLJ)
           (net.uncontended.precipice.metrics Metrics Rolling IntervalIterator)
           (net.uncontended.precipice.metrics.counts PartitionedCount
                                                     NoOpCounter
                                                     TotalCounts
                                                     RollingCounts CountRecorder)
           (net.uncontended.precipice.metrics.latency TotalLatency
                                                      NoOpLatency
                                                      ConcurrentHistogram
                                                      PartitionedLatency)
           (net.uncontended.precipice.metrics.tools Recorder)
           (java.util.concurrent TimeUnit)
           (java.util Iterator NoSuchElementException)))

(set! *warn-on-reflection* true)

(defn- to-millis [nanos]
  (.toMillis TimeUnit/NANOSECONDS nanos))

(defn- counter-to-map [^PartitionedCount counts start-millis end-millis]
  {:counts
   (persistent!
     (reduce (fn [acc ^ToCLJ e]
               (assoc! acc (.keyword e) (.getCount counts e)))
             (transient {})
             (.getEnumConstants (.getMetricClazz counts))))
   :start-millis start-millis
   :end-millis end-millis})

(defn- counter-to-count-fn [metric]
  (fn [^PartitionedCount counts start-millis end-millis]
    {:start-millis start-millis
     :end-millis end-millis
     :count (when metric (.getCount counts metric))}))

(defn- switcher [current-millis {:keys [start-millis precipice-metrics]}]
  (let [^Metrics m precipice-metrics]
    (if-not (instance? Recorder m)
      {:metrics m
       :interval-start start-millis}
      {:metrics (.activeInterval ^Recorder m)
       :interval-start (- (.toMillis TimeUnit/NANOSECONDS
                                     (- (.activeIntervalStart ^Recorder m)
                                        (System/nanoTime)))
                          current-millis)})))

(deftype SingleIterator
  [iterator-start-millis start-millis func metrics ^:unsynchronized-mutable is-realized?]
  Iterator
  (next [this]
    (if is-realized?
      (throw (NoSuchElementException.))
      (do (set! is-realized? true)
          (func metrics start-millis iterator-start-millis))))
  (hasNext [this]
    (not is-realized?))
  (remove [_]
    (throw (UnsupportedOperationException. "remove"))))

(deftype RollingIterator [iterator-start-millis func ^IntervalIterator iterator]
  Iterator
  (next [this]
    (let [start-millis (+ iterator-start-millis
                          (to-millis (.intervalStart iterator)))
          end-millis (+ iterator-start-millis
                        (to-millis (.intervalEnd iterator)))]
      (func (.next iterator) start-millis end-millis)))
  (hasNext [this]
    (.hasNext iterator))
  (remove [_]
    (throw (UnsupportedOperationException. "remove"))))


(defn get-count [{:keys [precipice-metrics key->enum] :as metrics} metric]
  (let [^Metrics precipice-metrics precipice-metrics
        current-millis (System/currentTimeMillis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          (counter-to-count-fn (get key->enum metric))
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (switcher current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            (counter-to-count-fn (get key->enum metric))
            metrics
            false))))))

(defn get-counts [metrics]
  (let [{:keys [precipice-metrics]} metrics
        current-millis (System/currentTimeMillis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          counter-to-map
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (switcher current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            counter-to-map
            metrics
            false))))))

(defn decorate-counts [^Metrics precipice-metrics]
  (let [key->enum (enums/enum-class-to-keyword->enum
                    (.getMetricClazz precipice-metrics))]
    {:key->enum key->enum
     :precipice-metrics precipice-metrics
     :start-millis (System/currentTimeMillis)}))

(defn no-op-counts
  ([] (no-op-counts EmptyEnum))
  ([^Class enum-class]
   (decorate-counts (TotalCounts. (NoOpCounter. enum-class)))))

(defn count-metrics [^Class enum-class]
  (if-not (identical? enum-class EmptyEnum)
    (decorate-counts (TotalCounts. enum-class))
    (no-op-counts)))

(defn count-recorder [^Class enum-class]
  (decorate-counts (.build (CountRecorder/builder enum-class))))

(defn rolling-counts
  ([enum-class] (rolling-counts enum-class (* 60 15) 1 :seconds))
  ([^Class enum-class slots-to-track resolution time-unit]
   (if-not (identical? enum-class EmptyEnum)
     (let [^TimeUnit time-unit (utils/->time-unit time-unit)
           nanos-per (.toNanos time-unit (long resolution))]
       (decorate-counts
         (RollingCounts. enum-class (int slots-to-track) nanos-per)))
     (no-op-counts))))

(defprotocol LatencyView
  (get-latency [this metric percentile]))

(defn- latency-at-percentile
  [^PartitionedLatency latency-metrics metric percentile key->enum]
  (when-let [enum (get key->enum metric)]
    (.getValueAtPercentile latency-metrics enum percentile)))

(defn- precipice-metrics [enum-class]
  (TotalLatency. (ConcurrentHistogram. enum-class)))

(defn- decorate-latency [^Metrics precipice-metrics]
  (let [key->enum (enums/enum-class-to-keyword->enum
                    (.getMetricClazz precipice-metrics))]
    (with-meta
      (reify
        LatencyView
        (get-latency [this metric percentile]
          (latency-at-percentile precipice-metrics metric percentile key->enum)))
      {:precipice-metrics precipice-metrics})))

(defn no-op-latency-metrics
  ([] (no-op-latency-metrics EmptyEnum))
  ([^Class enum-class]
   (let [precipice-metrics (TotalLatency. (NoOpLatency. enum-class))]
     (decorate-latency precipice-metrics))))

(defn latency-metrics [enum-class]
  (if-not (identical? enum-class EmptyEnum)
    (let [precipice-metrics (precipice-metrics enum-class)]
      (decorate-latency precipice-metrics))
    (no-op-latency-metrics)))