# Beehive

Beehive is a Clojure façade for the Precipice library. [Precipice](https://github.com/tbrooks8/Precipice) is a
library that provides monitoring and back pressure for task execution.

## Version

This library has not yet hit alpha. It is used in production at Staples SparX. However, the API may still change.

![Clojars Project](http://clojars.org/net.uncontended/beehive/latest-version.svg)

## Usage

```clojure
(ns your-namespace
  (:require [beehive.hive :as hive]
            [beehive.circuit-breaker :as breaker]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore])
  (:import (java.util.concurrent TimeUnit)
            (java.io IOException)
            (java.net SocketTimeoutException)))

(def example-beehive
  (hive/lett [result-class {:success true :error false}
                rejected-class #{:max-concurrency :circuit-open}]
      (-> (hive/beehive "Beehive Name" result-class rejected-class)
          (hive/set-result-counts (metrics/rolling-counts result-class))
          (hive/set-rejected-metrics (metrics/total-counts rejected-class))
          (hive/set-result-latency (metrics/latency-metrics result-class))
          (hive/add-backpressure :semaphore (semaphore/semaphore 5 :max-concurrency))
          (hive/add-backpressure :breaker (breaker/default-breaker
                                            {:failure-percentage-threshold 20
                                             :backoff-time-millis 3000}
                                            :max-concurrency))
          hive/map->hive)))

(defn- perform-http [completable]
  (try
    (let [http-response (make-http-request)]
      ;; Do something that can fail like an http request
      (hive/complete! completable :success http-response)
      http-response)
    (catch SocketTimeoutException e
      (hive/complete! completable :timeout e))
    (catch IOException e
      (hive/complete! completable :error e))))

(defn execute-synchronous-risky-task []
  (let [c (hive/acquire-completable example-beehive 1)]
    (if (:rejected? c)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason c)))
      (do (perform-http c)
          (hive/to-result-view c)))))

(defn execute-asynchronous-risky-task []
  (let [p (hive/acquire-promise example-beehive 1)]
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
(:count (first (metrics/count-seq (hive/rejected-counts example-beehive) :success)))

;; Returns the number rejected by the semaphore due to max-concurrency being violated
(:count (first (metrics/count-seq (hive/rejected-counts example-beehive) :max-concurrency)))
```

## License

Copyright © 2014 Tim Brooks

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.