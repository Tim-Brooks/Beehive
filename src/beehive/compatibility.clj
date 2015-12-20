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

(ns beehive.compatibility
  (:import (net.uncontended.precipice ResilientAction
                                      RejectedActionException
                                      RejectionReason)
           (net.uncontended.precipice.pattern ResilientPatternAction)
           (net.uncontended.precipice.timeout ActionTimeoutException)))

(declare rejected-exception->reason)

(defn wrap-pattern-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context] (action-fn context))))

(defn wrap-run-pattern-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context]
      (try
        (let [result (action-fn context)]
          {:status :success :result result :success? true})
        (catch ActionTimeoutException _
          {:status :timeout :timeout? true})
        (catch Throwable e
          {:status :error :error e :error? true})))))

(defn wrap-action-fn [action-fn]
  (reify ResilientAction
    (run [_] (action-fn))))

(defn wrap-run-action-fn [action-fn]
  (reify ResilientAction
    (run [_]
      (try
        (let [result (action-fn)]
          {:status :success :result result :success? true})
        (catch ActionTimeoutException _
          {:status :timeout :timeout? true})
        (catch Throwable e
          {:status :error :error e :error? true})))))

(defn rejected-exception->reason [^RejectedActionException e]
  (let [reason (.reason e)]
    (cond
      (identical? RejectionReason/CIRCUIT_OPEN reason)
      :circuit-open
      (identical? RejectionReason/MAX_CONCURRENCY_LEVEL_EXCEEDED reason)
      :max-concurrency-level-exceeded
      (identical? RejectionReason/QUEUE_FULL reason)
      :queue-full
      (identical? RejectionReason/SERVICE_SHUTDOWN reason)
      :service-shutdown
      (identical? RejectionReason/ALL_SERVICES_REJECTED reason)
      :all-services-rejected)))
