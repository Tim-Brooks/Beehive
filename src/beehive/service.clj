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
           (net.uncontended.precipice.circuit BreakerConfig
                                              CircuitBreaker
                                              DefaultCircuitBreaker
                                              BreakerConfigBuilder
                                              NoOpCircuitBreaker)
           (net.uncontended.precipice.metrics DefaultActionMetrics)
           (net.uncontended.precipice.concurrent PrecipiceFuture)
           (net.uncontended.precipice MultiService
                                      ResilientAction
                                      RejectedActionException
                                      Services
                                      ServiceProperties
                                      Service)
           (net.uncontended.precipice.timeout ActionTimeoutException)))

(set! *warn-on-reflection* true)

(defprotocol CLJService
  (submit-action [this action-fn timeout-millis])
  (run-action [this action-fn])
  (metrics [this] [this time time-unit])
  (latency [this])
  (remaining-capacity [this])
  (pending-count [this])
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

(deftype CLJServiceImpl
  [^MultiService service metrics-config ^CLJBreaker breaker]
  CLJService
  (submit-action [this action-fn timeout-millis]
    (try
      (f/->BeehiveFuture
        ^PrecipiceFuture
        (.submit service
                 ^ResilientAction (c/wrap-action-fn action-fn)
                 (long timeout-millis)))
      (catch RejectedActionException e
        (f/rejected-action-future e))))
  (run-action [_ action-fn]
    (try
      (.run service (c/wrap-run-action-fn action-fn))
      (catch RejectedActionException e
        {:status :rejected
         :rejected? true
         :rejected-reason (c/rejected-exception->reason e)})))
  (metrics [this]
    (let [{:keys [slots-to-track resolution time-unit]} metrics-config]
      (metrics this (* slots-to-track resolution) time-unit)))
  (metrics [_ time time-unit]
    (into {} (map (fn [[k v]] [(keyword k) v])
                  (.snapshot (.getActionMetrics service)
                             time
                             (utils/->time-unit time-unit)))))
  (latency [_]
    (let [snapshot (.latencySnapshot (.getLatencyMetrics service))]
      {:latency-max (.latencyMax snapshot)
       :latency-50 (.latency50 snapshot)
       :latency-90 (.latency90 snapshot)
       :latency-99 (.latency99 snapshot)
       :latency-99-9 (.latency999 snapshot)
       :latency-99-99 (.latency9999 snapshot)
       :latency-99-999 (.latency99999 snapshot)
       :latency-mean (.latencyMean snapshot)}))
  (pending-count [_] (.currentlyPending service))
  (remaining-capacity [_] (.remainingCapacity service))
  (shutdown [_] (.shutdown service))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :service service
      :breaker breaker
      default)))

(defn close-circuit! [^CLJServiceImpl service]
  (.forceClosed ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn open-circuit! [^CLJServiceImpl service]
  (.forceOpen ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn java-service->clj-service [^Service service metrics-config]
  (->CLJServiceImpl service
                    metrics-config
                    (->CLJBreaker (.getCircuitBreaker service))))

(defn circuit-breaker
  [{:keys [trailing-period-millis
           failure-threshold
           failure-percentage-threshold
           backoff-time-millis
           health-refresh-millis
           sample-size-threshold]}]
  (let [config (cond-> (BreakerConfigBuilder.)
                       trailing-period-millis
                       (.trailingPeriodMillis trailing-period-millis)

                       failure-threshold
                       (.failureThreshold failure-threshold)

                       failure-percentage-threshold
                       (.failurePercentageThreshold failure-percentage-threshold)

                       backoff-time-millis
                       (.backOffTimeMillis backoff-time-millis)

                       health-refresh-millis
                       (.healthRefreshMillis health-refresh-millis)

                       sample-size-threshold
                       (.sampleSizeThreshold sample-size-threshold))]
    (DefaultCircuitBreaker. ^BreakerConfig (.build config))))

(defn service
  [name
   pool-size
   max-concurrency
   breaker-config
   {:keys [slots-to-track resolution time-unit] :as metrics-config}]
  (let [metrics (DefaultActionMetrics. slots-to-track resolution (utils/->time-unit time-unit))
        breaker (circuit-breaker breaker-config)
        properties (doto (ServiceProperties.)
                     (.actionMetrics metrics)
                     (.circuitBreaker breaker)
                     (.concurrencyLevel (int max-concurrency)))
        service (Services/defaultService ^String name
                                         (int pool-size)
                                         properties)]
    (java-service->clj-service service metrics-config)))

(defn service-with-no-opt-breaker
  [name
   pool-size
   max-concurrency
   {:keys [slots-to-track resolution time-unit] :as metrics-config}]
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
    (java-service->clj-service service metrics-config)))
