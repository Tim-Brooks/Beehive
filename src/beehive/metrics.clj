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
                                                      PartitionedLatency LatencyRecorder)
           (net.uncontended.precipice.metrics.tools Recorder)
           (java.util.concurrent TimeUnit)
           (java.util Iterator NoSuchElementException)))

(set! *warn-on-reflection* true)

(declare counter-to-map)

(defn- current-millis []
  (System/currentTimeMillis))

(defn- to-millis [nanos]
  (.toMillis TimeUnit/NANOSECONDS nanos))

(defn- next-counts-fn [counts start-millis end-millis]
  {:counts (counter-to-map counts)
   :start-millis start-millis
   :end-millis end-millis})

(defn- create-next-count-fn [metric]
  (fn [^PartitionedCount counts start-millis end-millis]
    {:start-millis start-millis
     :end-millis end-millis
     :count (when metric (.getCount counts metric))}))

(defn- prep-metrics [current-millis {:keys [start-millis precipice-metrics]}]
  (let [^Metrics m precipice-metrics]
    (if-not (instance? Recorder m)
      {:metrics m
       :interval-start start-millis}
      {:metrics (.activeInterval ^Recorder m)
       :interval-start (- (to-millis (- (.activeIntervalStart ^Recorder m)
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

(deftype RollingIterator [current-millis func ^IntervalIterator iterator]
  Iterator
  (next [this]
    (let [start (+ current-millis (to-millis (.intervalStart iterator)))
          end (+ current-millis (to-millis (.intervalEnd iterator)))]
      (func (.next iterator) start end)))
  (hasNext [this]
    (.hasNext iterator))
  (remove [_]
    (throw (UnsupportedOperationException. "remove"))))

(defn counter-to-map [^PartitionedCount counts]
  (persistent!
    (reduce (fn [acc ^ToCLJ e]
              (assoc! acc (.keyword e) (.getCount counts e)))
            (transient {})
            (.getEnumConstants (.getMetricClazz counts)))))

(defn count-seq [{:keys [precipice-metrics key->enum] :as metrics} metric]
  (let [^Metrics precipice-metrics precipice-metrics
        current-millis (current-millis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          (create-next-count-fn (get key->enum metric))
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (prep-metrics current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            (create-next-count-fn (get key->enum metric))
            metrics
            false))))))

(defn counts-seq [metrics]
  (let [{:keys [precipice-metrics]} metrics
        current-millis (current-millis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          next-counts-fn
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (prep-metrics current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            next-counts-fn
            metrics
            false))))))

(defn decorate-metrics [^Metrics precipice-metrics]
  (let [key->enum (enums/enum-class-to-keyword->enum
                    (.getMetricClazz precipice-metrics))]
    {:key->enum key->enum
     :precipice-metrics precipice-metrics
     :start-millis (current-millis)}))

(defn no-op-counts
  ([] (no-op-counts EmptyEnum))
  ([^Class enum-class]
   (decorate-metrics (TotalCounts. (NoOpCounter. enum-class)))))

(defn total-counts [^Class enum-class]
  (decorate-metrics (TotalCounts. enum-class)))

(defn count-recorder [^Class enum-class]
  (decorate-metrics (.build (CountRecorder/builder enum-class))))

(defn rolling-counts
  ([enum-class] (rolling-counts enum-class (* 60 15) 1 :seconds))
  ([^Class enum-class slots-to-track resolution time-unit]
   (let [^TimeUnit time-unit (utils/->time-unit time-unit)]
     (decorate-metrics
       (.build
         (doto (RollingCounts/builder enum-class)
           (.bucketCount (int slots-to-track))
           (.bucketResolution (long resolution) time-unit)))))))

(defn- latency-to-map [^PartitionedLatency latency start-millis end-millis]
  {:latencies
   (persistent!
     (reduce (fn [acc ^ToCLJ e]
               (assoc!
                 acc
                 (.keyword e)
                 {:10 (.getValueAtPercentile latency e 1.0)
                  :50 (.getValueAtPercentile latency e 5.0)
                  :90 (.getValueAtPercentile latency e 90.0)
                  :99 (.getValueAtPercentile latency e 99.0)
                  :99.9 (.getValueAtPercentile latency e 99.9)
                  :99.99 (.getValueAtPercentile latency e 99.99)
                  :99.999 (.getValueAtPercentile latency e 99.999)}))
             (transient {})
             (.getEnumConstants (.getMetricClazz latency))))
   :start-millis start-millis
   :end-millis end-millis})

(defn- latency-to-latency-fn [metric]
  (fn [^PartitionedLatency latency start-millis end-millis]
    {:start-millis start-millis
     :end-millis end-millis
     :count (when metric
              {:10 (.getValueAtPercentile latency metric 1.0)
               :50 (.getValueAtPercentile latency metric 5.0)
               :90 (.getValueAtPercentile latency metric 90.0)
               :99 (.getValueAtPercentile latency metric 99.0)
               :99.9 (.getValueAtPercentile latency metric 99.9)
               :99.99 (.getValueAtPercentile latency metric 99.99)
               :99.999 (.getValueAtPercentile latency metric 99.999)})}))

(defn get-latency [{:keys [precipice-metrics key->enum] :as metrics} metric]
  (let [^Metrics precipice-metrics precipice-metrics
        current-millis (current-millis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          (latency-to-latency-fn (get key->enum metric))
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (prep-metrics current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            (latency-to-latency-fn (get key->enum metric))
            metrics
            false))))))

(defn get-latencies [metrics]
  (let [{:keys [precipice-metrics]} metrics
        current-millis (current-millis)]
    (iterator-seq
      (if (instance? Rolling precipice-metrics)
        (->RollingIterator
          current-millis
          latency-to-map
          (.intervals ^Rolling precipice-metrics))
        (let [{:keys [interval-start metrics]} (prep-metrics current-millis metrics)]
          (->SingleIterator
            current-millis
            interval-start
            latency-to-map
            metrics
            false))))))

(defn no-op-latency-metrics
  ([] (no-op-latency-metrics EmptyEnum))
  ([^Class enum-class]
   (let [precipice-metrics (TotalLatency. (NoOpLatency. enum-class))]
     (decorate-metrics precipice-metrics))))

(defn latency-metrics [enum-class]
  (decorate-metrics (TotalLatency. (ConcurrentHistogram. enum-class))))

(defn latency-recorder [^Class enum-class]
  (decorate-metrics (.build (LatencyRecorder/builder enum-class))))

(defn recorder-swap!
  ([metrics]
   (let [^Recorder recorder (:precipice-metrics metrics)]
     (.captureInterval recorder)))
  ([metrics value]
   (let [^Recorder recorder (:precipice-metrics metrics)]
     (.captureInterval recorder value))))