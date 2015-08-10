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
           (net.uncontended.precipice.core.circuit BreakerConfig
                                                   CircuitBreaker BreakerConfigBuilder NoOpCircuitBreaker)
           (net.uncontended.precipice.core.metrics ActionMetrics Metric DefaultActionMetrics)
           (net.uncontended.precipice.core.concurrent PrecipiceFuture)
           (net.uncontended.precipice.core MultiService
                                           ResilientAction
                                           RejectedActionException Services ServiceProperties)))

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

(deftype CLJMetrics [^ActionMetrics metrics seconds-tracked]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :snapshot (.snapshot metrics seconds-tracked TimeUnit/SECONDS)
      :errors (.getMetricCountForTimePeriod metrics
                                            Metric/ERROR
                                            seconds-tracked
                                            TimeUnit/SECONDS)
      :successes (.getMetricCountForTimePeriod metrics
                                               Metric/SUCCESS
                                               seconds-tracked
                                               TimeUnit/SECONDS)
      :timeouts (.getMetricCountForTimePeriod metrics
                                              Metric/TIMEOUT
                                              seconds-tracked
                                              TimeUnit/SECONDS)
      :circuit-open (.getMetricCountForTimePeriod metrics
                                                  Metric/CIRCUIT_OPEN
                                                  seconds-tracked
                                                  TimeUnit/SECONDS)
      :queue-full (.getMetricCountForTimePeriod metrics
                                                Metric/QUEUE_FULL
                                                seconds-tracked
                                                TimeUnit/SECONDS)
      :max-concurrency-level-exceeded (.getMetricCountForTimePeriod
                                        metrics
                                        Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED
                                        seconds-tracked
                                        TimeUnit/SECONDS)
      default))
  Object
  (toString [this]
    (str {:errors (.getMetricCountForTimePeriod metrics
                                                Metric/ERROR
                                                seconds-tracked
                                                TimeUnit/SECONDS)
          :successes (.getMetricCountForTimePeriod metrics
                                                   Metric/SUCCESS
                                                   seconds-tracked
                                                   TimeUnit/SECONDS)
          :timeouts (.getMetricCountForTimePeriod metrics
                                                  Metric/TIMEOUT
                                                  seconds-tracked
                                                  TimeUnit/SECONDS)
          :circuit-open (.getMetricCountForTimePeriod metrics
                                                      Metric/CIRCUIT_OPEN
                                                      seconds-tracked
                                                      TimeUnit/SECONDS)
          :queue-full (.getMetricCountForTimePeriod metrics
                                                    Metric/QUEUE_FULL
                                                    seconds-tracked
                                                    TimeUnit/SECONDS)
          :max-concurrency-level-exceeded (.getMetricCountForTimePeriod
                                            metrics
                                            Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED
                                            seconds-tracked
                                            TimeUnit/SECONDS)})))

(deftype CLJServiceImpl
  [^MultiService executor ^CLJMetrics metrics ^CLJBreaker breaker]
  CLJService
  (submit-action [this action-fn timeout-millis]
    (try
      (f/->CLJResilientFuture
        ^PrecipiceFuture
        (.submit executor
                 ^ResilientAction (c/wrap-action-fn action-fn)
                 (long timeout-millis)))
      (catch RejectedActionException e
        (f/rejected-action-future (.reason e)))))
  (run-action [_ action-fn]
    (.run executor (c/wrap-action-fn action-fn)))
  (shutdown [_] (.shutdown executor))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :metrics metrics
      :service-executor executor
      :breaker breaker
      default)))

(defn swap-breaker-config!
  [{:keys [circuit-breaker]}
   {:keys [trailing-period-millis
           failure-threshold
           failure-percentage-threshold
           backoff-time-millis
           health-refresh-millis]}]
  (.setBreakerConfig
    ^CircuitBreaker circuit-breaker
    ^BreakerConfig (doto (BreakerConfigBuilder.)
                     (.trailingPeriodMillis trailing-period-millis)
                     (.failureThreshold failure-threshold)
                     (.failurePercentageThreshold failure-percentage-threshold)
                     (.backOffTimeMillis backoff-time-millis)
                     (.healthRefreshMillis health-refresh-millis)
                     (.build))))

(defn close-circuit! [^CLJServiceImpl service]
  (.forceClosed ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn open-circuit! [^CLJServiceImpl service]
  (.forceOpen ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn service-executor
  [name
   pool-size
   max-concurrency
   {:keys [failure-percentage-threshold backoff-time-millis]}
   {:keys [slots-to-track resolution time-unit]}]
  (let [metrics (DefaultActionMetrics. slots-to-track resolution (utils/->time-unit time-unit))
        properties (doto (ServiceProperties.)
                     (.actionMetrics metrics)
                     (.concurrencyLevel (int max-concurrency)))
        executor (Services/defaultService ^String name
                                          (int pool-size)
                                          properties)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor)
                                    (.convert TimeUnit/SECONDS
                                              slots-to-track
                                              (utils/->time-unit time-unit)))
                      (->CLJBreaker (.getCircuitBreaker executor)))))

(defn executor-with-no-opt-breaker
  [name pool-size max-concurrency {:keys [slots-to-track resolution time-unit]}]
  (let [metrics (DefaultActionMetrics. slots-to-track
                                       resolution
                                       (utils/->time-unit time-unit))
        properties (doto (ServiceProperties.)
                     (.actionMetrics metrics)
                     (.concurrencyLevel (int max-concurrency))
                     (.circuitBreaker (NoOpCircuitBreaker.)))
        executor (Services/defaultService ^String name
                                          (int pool-size)
                                          properties)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor)
                                    (.convert TimeUnit/SECONDS
                                              slots-to-track
                                              (utils/->time-unit time-unit)))
                      (->CLJBreaker (.getCircuitBreaker executor)))))
