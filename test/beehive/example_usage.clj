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

(ns beehive.example-usage
  (:require [beehive.hive :as hive]
            [beehive.circuit-breaker :as breaker]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore]
            [beehive.hive :as beehive])
  (:import (java.util.concurrent TimeUnit)
           (java.io IOException)
           (java.net SocketTimeoutException)))

(defn make-http-request []
  ;; Do something that can fail like an http request
  )

(def example-beehive
  (hive/beehive
    "Beehive Name"
    (hive/results
      {:success true :error false}
      (metrics/rolling-count-metrics)
      (metrics/latency-metrics (.toNanos TimeUnit/MINUTES 1) 3))
    (hive/create-back-pressure
      #{:max-concurrency :circuit-open}
      (metrics/rolling-count-metrics)
      (semaphore/semaphore 5 :max-concurrency)
      (breaker/default-breaker
        {:failure-percentage-threshold 20
         :backoff-time-millis 3000}
        :max-concurrency))))

(defn- perform-http [completable]
  (try
    (let [http-response (make-http-request)]
      ;; Do something that can fail like an http request
      (hive/complete! completable :success http-response)
      http-response)
    (catch SocketTimeoutException e
      (beehive/complete! completable :timeout e))
    (catch IOException e
      (beehive/complete! completable :error e))))

(defn execute-synchronous-risky-task []
  (let [c (hive/completable example-beehive 1)]
    (if (:rejected? c)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason c)))
      (do (perform-http c)
          (hive/to-result-view c)))))

(defn execute-asynchronous-risky-task []
  (let [p (hive/promise example-beehive 1)]
    (if (:rejected? p)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason p)))
      (do (future (perform-http p))
          (hive/to-future p)))))

;; Will block until the completion (or error) of the http request
(execute-synchronous-risky-task)

;; Will return a future representing the execution of the http request
(execute-asynchronous-risky-task)

;; Returns the number of successes
(metrics/total-count (hive/result-metrics example-beehive) :success)

;; Returns the number rejected by the semaphore due to max-concurrency being violated
(metrics/total-count (hive/rejected-metrics example-beehive) :max-concurrency)

;; Returns a latency percentile map for errors
(metrics/latency (hive/latency-metrics example-beehive) :error)