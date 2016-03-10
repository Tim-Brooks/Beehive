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
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! go]]
            [beehive.hive :as hive]
            [beehive.circuit-breaker :as breaker]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore]
            [beehive.hive :as beehive])
  (:import (java.util.concurrent TimeUnit)
           (java.io IOException)))

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



(defn execute-synchronous-risky-task []
  (let [c (hive/completable example-beehive 1)]
    (if (:rejected? c)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason c)))
      (try
        (let [http-response (make-http-request)]
          ;; Do something that can fail like an http request
          (hive/complete! c :success http-response)
          http-response)
        (catch IOException e
          (beehive/complete! c :error e))))))

(defn execute-asynchronous-risky-task []
  (let [p (hive/completable example-beehive 1)]
    (if (:rejected? p)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason p)))
      (do (future
            (try
              (let [http-response (make-http-request)]
                ;; Do something that can fail like an http request
                (hive/complete! p :success http-response)
                http-response)
              (catch IOException e
                (beehive/complete! p :error e))))
          @(hive/future p)))))

;; Returns the number of successes
(metrics/total-count (hive/result-metrics example-beehive) :success)

;; Returns the number rejected by the semaphore due to max-concurrency being violated
(metrics/total-count (hive/rejected-metrics example-beehive) :max-concurrency)

;; Returns a latency percentile map for errors
(metrics/latency (hive/latency-metrics example-beehive) :error)