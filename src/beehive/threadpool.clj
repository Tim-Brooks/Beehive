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
  (:require [beehive.future :as f]
            [beehive.hive])
  (:import (net.uncontended.precipice.concurrent PrecipiceFuture)
           (net.uncontended.precipice GuardRail)
           (net.uncontended.precipice.threadpool ThreadPoolService)
           (net.uncontended.precipice.timeout TimeoutService)
           (net.uncontended.precipice.rejected RejectedException)
           (beehive.hive Hive)))

(set! *warn-on-reflection* true)

(defn submit
  ([threadpool fn]
    (submit threadpool fn TimeoutService/NO_TIMEOUT))
  ([thread-pool fn timeout-millis]
   (let [^ThreadPoolService thread-pool thread-pool]
     (try
       (f/->BeehiveFuture
         ^PrecipiceFuture (.submit thread-pool fn (long timeout-millis)))
       (catch RejectedException e
         (f/rejected-action-future e))))))

(defn shutdown [thread-pool]
  (.shutdown ^ThreadPoolService thread-pool))

(defn threadpool [guard-rail pool-size max-concurrency]
  (let [guard-rail ^GuardRail guard-rail]
    {:result-metrics (.getResultMetrics guard-rail)
     :rejected-metrics (.getRejectedMetrics guard-rail)
     :latency-metrics (.getLatencyMetrics guard-rail)
     :thread-pool (ThreadPoolService. pool-size
                                      (+ max-concurrency 2)
                                      guard-rail)}))

(defn threadpool [pool-size queue-size ^Hive beehive]
  (ThreadPoolService. pool-size queue-size (.guard_rail beehive)))
