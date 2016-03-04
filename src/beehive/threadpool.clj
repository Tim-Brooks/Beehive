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
           (net.uncontended.precipice.threadpool ThreadPoolService)
           (net.uncontended.precipice.timeout TimeoutService)
           (net.uncontended.precipice.result TimeoutableResult)
           (net.uncontended.precipice.rejected RejectedException)))

(set! *warn-on-reflection* true)

(def key-enums
  {:success '(. net.uncontended.precipice.result.TimeoutableResult SUCCESS)
   :error '(. net.uncontended.precipice.result.TimeoutableResult ERROR)
   :timeout '(. net.uncontended.precipice.result.TimeoutableResult TIMEOUT)})

(defn- status [status-enum]
  (cond
    (identical? TimeoutableResult/SUCCESS status-enum) :success
    (identical? TimeoutableResult/ERROR status-enum) :error
    (identical? TimeoutableResult/TIMEOUT status-enum) :timeout))

(defn submit
  ([thread-pool fn]
   (submit thread-pool fn TimeoutService/NO_TIMEOUT))
  ([{:keys [thread-pool]} fn timeout-millis]
   (let [^ThreadPoolService thread-pool thread-pool]
     (try
       (f/->BeehiveFuture
         ^PrecipiceFuture (.submit thread-pool fn (long timeout-millis))
         status)
       (catch RejectedException e
         (f/rejected-future e))))))

(defn shutdown [{:keys [thread-pool]}]
  (.shutdown ^ThreadPoolService thread-pool))

(defn threadpool [pool-size queue-size beehive]
  (assoc beehive
    :thread-pool (ThreadPoolService. pool-size queue-size (:guard-rail beehive))))

(defmacro threadpool-results [metrics-seq & latency-metrics-seq]
  (let [metrics-fn (first metrics-seq)
        metric-fn-args (rest metrics-seq)
        latency-metrics-seq (first latency-metrics-seq)
        latency-metrics-fn (or (first latency-metrics-seq) identity)
        latency-metrics-args (rest latency-metrics-seq)]
    `(cond-> {:result-key->enum ~key-enums
              :result-metrics (~metrics-fn ~key-enums ~@metric-fn-args)}
             ~latency-metrics-seq
             (assoc :latency-metrics (~latency-metrics-fn
                                       ~key-enums
                                       ~@latency-metrics-args)))))
