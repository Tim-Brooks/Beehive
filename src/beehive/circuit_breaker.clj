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
  (:import (net.uncontended.precipice.circuit BreakerConfig
                                              BreakerConfigBuilder
                                              DefaultCircuitBreaker
                                              NoOpCircuitBreaker)))

(defn no-opt-breaker []
  (NoOpCircuitBreaker.))

(defn default-breaker
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