# Beehive

Beehive is a Clojure façade for the Precipice library. [Precipice](https://github.com/tbrooks8/Precipice) is a
library that provides monitoring and back pressure for task execution.

## Version

This library has not yet hit alpha. It is used in production at Staples SparX. However, the API may still change.

![Clojars Project](http://clojars.org/net.uncontended/beehive/latest-version.svg)

## Usage

```clojure
(ns your-namespace
  (:require [beehive.hive :as beehive]))

(def example-beehive
  (hive/beehive
    "Beehive Name"
    (hive/results
      {:success true :error false}
      (metrics/rolling-count-metrics))
    (hive/create-back-pressure
      #{:max-concurrency :circuit-open}
      (metrics/rolling-count-metrics)
      (semaphore/semaphore 5 :max-concurrency)
      (breaker/default-breaker
         (create-breaker-config
           {:failure-percentage-threshold 20
            :backoff-time-millis 3000})
         :max-concurrency))))

(defn execute-synchronous-risky-task []
  (let [c (beehive/completable example-beehive 1)]
    (if (:rejected? c)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason c)))
      (try
        ;; Do something that can fail like an http request
        (beehive/complete! c :success http-response)
        http-response
        (catch IOException e
          (beehive/complete! c :error e)))))

(defn execute-asynchronous-risky-task []
  (let [p (beehive/promise example-beehive 1)]
    (if (:rejected? c)
      (do
        (println "The beehive has told us not do execute this task right now")
        (println "The rejected reason is: " (:rejected-reason c)))
      (future
        (try
          ;; Do something that can fail like an http request
          (beehive/complete! p :success http-response)
          http-response
          (catch IOException e
            (beehive/complete! p :error e))))
      @(beehive/future p))))
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