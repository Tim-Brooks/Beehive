;; Copyright 2014 Timothy Brooks
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

(ns beehive.threadpool
  (:require [beehive.circuit-breaker :as cb]
            [beehive.future :as f]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore])
  (:import (java.util.concurrent TimeUnit)
           (net.uncontended.precipice.concurrent PrecipiceFuture)
           (net.uncontended.precipice GuardRail GuardRailBuilder)
           (net.uncontended.precipice.threadpool ThreadPoolService)
           (net.uncontended.precipice.timeout TimeoutService)
           (net.uncontended.precipice.rejected RejectedException)))

(set! *warn-on-reflection* true)

(defn submit
  ([threadpool fn]
    (submit threadpool fn TimeoutService/NO_TIMEOUT))
  ([{:keys [thread-pool]} fn timeout-millis]
   (let [^ThreadPoolService thread-pool thread-pool]
     (try
       (f/->BeehiveFuture
         ^PrecipiceFuture (.submit thread-pool fn (long timeout-millis)))
       (catch RejectedException e
         (f/rejected-action-future e))))))

(defn shutdown [{:keys [thread-pool]}]
  (.shutdown ^ThreadPoolService thread-pool))

(defn threadpool [guard-rail pool-size max-concurrency]
  (let [guard-rail ^GuardRail guard-rail]
    {:result-metrics (.getResultMetrics guard-rail)
     :rejected-metrics (.getRejectedMetrics guard-rail)
     :latency-metrics (.getLatencyMetrics guard-rail)
     :thread-pool (ThreadPoolService. pool-size
                                      (+ max-concurrency 2)
                                      guard-rail)}))

(defn threadpool
  ([name pool-size max-concurrency]
   (threadpool name pool-size max-concurrency {:slots-to-track 3600
                                               :resolution 1
                                               :time-unit :seconds}))
  ([name pool-size max-concurrency metrics-config]
   (let [metrics (metrics/count-metrics metrics-config)
         rejected-metrics (metrics/count-metrics metrics-config)
         semaphore (semaphore/semaphore max-concurrency)
         latency (metrics/latency-metrics (.toNanos TimeUnit/HOURS 1) 2)
         guard-rail (-> (GuardRailBuilder.)
                        (.name name)
                        (.resultMetrics metrics)
                        (.rejectedMetrics rejected-metrics)
                        (.resultLatency latency)
                        (.addBackPressure semaphore)
                        (.build))]
     {:result-metrics metrics
      :rejected-metrics rejected-metrics
      :latency-metrics latency
      :backpresure {:semaphore semaphore}
      :thread-pool (ThreadPoolService. pool-size (+ max-concurrency 2) guard-rail)})))
