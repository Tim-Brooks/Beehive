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
  (:require [beehive.compatibility :as c]
            [beehive.future :as f]
            [beehive.utils :as utils])
  (:import (clojure.lang ILookup)
           (java.util.concurrent TimeUnit)
           (net.uncontended.precipice.circuit BreakerConfig
                                              CircuitBreaker
                                              DefaultCircuitBreaker
                                              BreakerConfigBuilder
                                              NoOpCircuitBreaker)
           (net.uncontended.precipice.metrics ActionMetrics
                                              Metric
                                              DefaultActionMetrics)
           (net.uncontended.precipice.concurrent PrecipiceFuture)
           (net.uncontended.precipice MultiService
                                      ResilientAction
                                      RejectedActionException
                                      Services
                                      ServiceProperties
                                      Service)))

(set! *warn-on-reflection* true)

(defprotocol CLJService
  (submit-action [this action-fn timeout-millis] [this action-fn callback timeout-millis])
  (run-action [this action-fn])
  (shutdown [this]))

(deftype CLJBreaker [^CircuitBreaker breaker]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :open? (.isOpen breaker)
      :config (let [^BreakerConfig config (.getBreakerConfig breaker)]
                {:time-period-in-millis (.trailingPeriodMillis config)
                 :failure-threshold (.failureThreshold config)
                 :time-to-pause-millis (.backOffTimeMillis config)})
      default))
  Object
  (toString [this]
    (str {:open? (.isOpen breaker)
          :config (let [^BreakerConfig config (.getBreakerConfig breaker)]
                    {:trailing-period-millis (.trailingPeriodMillis config)
                     :failure-threshold (.failureThreshold config)
                     :back-off-time-millis (.backOffTimeMillis config)
                     :failure-percentage-threshold (.failurePercentageThreshold config)
                     :health-refresh-millis (.healthRefreshMillis config)})})))

(deftype CLJMetrics [^ActionMetrics metrics]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :snapshot (.snapshot metrics 3600 TimeUnit/SECONDS)
      :errors (.getMetricCount metrics Metric/ERROR)
      :successes (.getMetricCount metrics Metric/SUCCESS)
      :timeouts (.getMetricCount metrics Metric/TIMEOUT)
      :circuit-open (.getMetricCount metrics Metric/CIRCUIT_OPEN)
      :queue-full (.getMetricCount metrics Metric/QUEUE_FULL)
      :max-concurrency-level-exceeded
      (.getMetricCount metrics Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED)
      default))
  Object
  (toString [this]
    (str
      {:errors (.getMetricCount metrics Metric/ERROR)
       :successes (.getMetricCount metrics Metric/SUCCESS)
       :timeouts (.getMetricCount metrics Metric/TIMEOUT)
       :circuit-open (.getMetricCount metrics Metric/CIRCUIT_OPEN)
       :queue-full (.getMetricCount metrics Metric/QUEUE_FULL)
       :max-concurrency-level-exceeded
       (.getMetricCount metrics Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED)})))

(deftype CLJServiceImpl
  [^MultiService service ^CLJMetrics metrics ^CLJBreaker breaker]
  CLJService
  (submit-action [this action-fn timeout-millis]
    (try
      (f/->CLJResilientFuture
        ^PrecipiceFuture
        (.submit service
                 ^ResilientAction (c/wrap-action-fn action-fn)
                 (long timeout-millis)))
      (catch RejectedActionException e
        (f/rejected-action-future (.reason e)))))
  (run-action [_ action-fn]
    (try
      (.run service (c/wrap-action-fn action-fn))
      (catch RejectedActionException e
        (c/rejected-exception->reason e))))
  (shutdown [_] (.shutdown service))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :metrics metrics
      :service service
      :breaker breaker
      default)))

(defn close-circuit! [^CLJServiceImpl service]
  (.forceClosed ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn open-circuit! [^CLJServiceImpl service]
  (.forceOpen ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn java-service->clj-service [^Service service]
  (->CLJServiceImpl service
                    (->CLJMetrics (.getActionMetrics service))
                    (->CLJBreaker (.getCircuitBreaker service))))

(defn circuit-breaker
  [{:keys [trailing-period-millis
           failure-threshold
           failure-percentage-threshold
           backoff-time-millis
           health-refresh-millis]
    :as breaker-config}]
  (DefaultCircuitBreaker.
    ^BreakerConfig (.build (cond-> (BreakerConfigBuilder.)
                             trailing-period-millis (.trailingPeriodMillis trailing-period-millis)
                             failure-threshold  (.failureThreshold failure-threshold)
                             failure-percentage-threshold (.failurePercentageThreshold failure-percentage-threshold)
                             backoff-time-millis (.backOffTimeMillis backoff-time-millis)
                             health-refresh-millis (.healthRefreshMillis health-refresh-millis)))))

(defn service
  [name
   pool-size
   max-concurrency
   breaker-config
   {:keys [slots-to-track resolution time-unit]}]
  (let [metrics (DefaultActionMetrics. slots-to-track resolution (utils/->time-unit time-unit))
        breaker (circuit-breaker breaker-config)
        properties (doto (ServiceProperties.)
                     (.actionMetrics metrics)
                     (.circuitBreaker breaker)
                     (.concurrencyLevel (int max-concurrency)))
        service (Services/defaultService ^String name
                                         (int pool-size)
                                         properties)]
    (java-service->clj-service service)))

(defn service-with-no-opt-breaker
  [name pool-size max-concurrency {:keys [slots-to-track resolution time-unit]}]
  (let [metrics (DefaultActionMetrics. slots-to-track
                                       resolution
                                       (utils/->time-unit time-unit))
        properties (doto (ServiceProperties.)
                     (.actionMetrics metrics)
                     (.concurrencyLevel (int max-concurrency))
                     (.circuitBreaker (NoOpCircuitBreaker.)))
        service (Services/defaultService ^String name
                                         (int pool-size)
                                         properties)]
    (java-service->clj-service service)))
