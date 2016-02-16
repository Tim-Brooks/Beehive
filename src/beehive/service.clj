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

(ns beehive.service
  (:require [beehive.circuit-breaker :as cb]
            [beehive.future :as f]
            [beehive.metrics :as metrics])
  (:import (net.uncontended.precipice.concurrent PrecipiceFuture)
           (net.uncontended.precipice GuardRailBuilder RejectedException Rejected)
           (net.uncontended.precipice.threadpool ThreadPoolService)
           (net.uncontended.precipice.semaphore LongSemaphore)))

(set! *warn-on-reflection* true)

(defn submit [{:keys [thread-pool]} fn timeout-millis]
  (let [^ThreadPoolService thread-pool thread-pool]
    (try
      (f/->BeehiveFuture
        ^PrecipiceFuture (.submit thread-pool fn (long timeout-millis)))
      (catch RejectedException e
        (f/rejected-action-future e)))))

(defn shutdown [{:keys [thread-pool]}]
  (.shutdown ^ThreadPoolService thread-pool))

(defn service
  [name pool-size max-concurrency breaker-config metrics-config]
  (let [metrics (metrics/count-metrics metrics-config)
        rejected-metrics (metrics/count-metrics metrics-config)
        breaker (cb/default-breaker breaker-config)
        semaphore (LongSemaphore. Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED
                                  max-concurrency)
        builder (doto (GuardRailBuilder.)
                  (.name name)
                  (.resultMetrics metrics)
                  (.rejectedMetrics rejected-metrics)
                  (.addBackPressure semaphore)
                  (.addBackPressure breaker))]
    {:result-metrics metrics
     :rejected-metrics rejected-metrics
     :circuit-breaker breaker
     :thread-pool (ThreadPoolService. pool-size
                                      (+ max-concurrency 2)
                                      (.build builder))}))
