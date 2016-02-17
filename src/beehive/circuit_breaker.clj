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

(ns beehive.circuit-breaker
  (:import (net.uncontended.precipice Rejected)
           (net.uncontended.precipice.circuit CircuitBreakerConfig
                                              CircuitBreakerConfigBuilder
                                              CircuitBreaker
                                              DefaultCircuitBreaker
                                              NoOpCircuitBreaker)))

(set! *warn-on-reflection* true)

(defn- create-breaker-config
  [{:keys [trailing-period-millis
           failure-threshold
           failure-percentage-threshold
           backoff-time-millis
           health-refresh-millis
           sample-size-threshold]}]
  (let [config (cond-> (CircuitBreakerConfigBuilder. Rejected/CIRCUIT_OPEN)
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
    (.build config)))

(defn no-opt-breaker []
  (NoOpCircuitBreaker.))

(defn default-breaker [config-map]
  (DefaultCircuitBreaker.
    ^CircuitBreakerConfig (create-breaker-config config-map)))

(defn close-circuit! [breaker]
  (.forceClosed ^CircuitBreaker breaker))

(defn open-circuit! [breaker]
  (.forceOpen ^CircuitBreaker breaker))

(defn set-config! [breaker config-map]
  (.setBreakerConfig ^CircuitBreaker breaker (create-breaker-config config-map)))

(defn get-config [breaker]
  (let [^CircuitBreakerConfig config (.getBreakerConfig ^CircuitBreaker breaker)]
    {:trailing-period-millis (.trailingPeriodMillis config)
     :failure-threshold (.failureThreshold config)
     :back-off-time-millis (.backOffTimeMillis config)
     :failure-percentage-threshold (.failurePercentageThreshold config)
     :health-refresh-millis (.healthRefreshMillis config)}))

(defn open? [breaker]
  (.isOpen ^CircuitBreaker breaker))

(defn closed? [breaker]
  (not (.isOpen ^CircuitBreaker breaker)))