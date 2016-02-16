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
            [clj-http.client :as http]
            [beehive.async :as fa]
            [beehive.core :as beehive]
            [beehive.service :as service]))

(def api-route (str "http://www.broadbandmap.gov/broadbandmap/"
                    "census/county/%s?format=json"))

(defonce service (atom nil))
(defonce service2 (atom nil))

(defn start-service []
  (let [service-name "Service with no circuit breaker"
        num-of-threads 1
        max-concurrency 100]
    (reset! service
            (beehive/service service-name num-of-threads max-concurrency)))
  (let [service-name "Service with circuit breaker"
        num-of-threads 1
        max-concurrency 100]
    (reset! service2
            (beehive/service service-name
                             num-of-threads
                             max-concurrency
                             :breaker {:failure-percentage-threshold 20
                                       :backoff-time-millis 2000}
                             :metrics {:slots-to-track 3600
                                       :resolution 500
                                       :time-unit :milliseconds}))))

(defn lookup-state-action [county]
  (fn [] (-> (http/get (format api-route county) {:as :json})
             :body
             :Results
             :county)))

(defn handle-success [success-channel]
  (go (loop []
        (let [success-future (<! success-channel)]
          (println "Success")
          (println (:result success-future))
          (recur)))))

(defn handle-error [err-channel]
  (go (loop []
        (let [error-future (<! err-channel)]
          (println "Error")
          (println (:error error-future))
          (println (:status error-future))
          (recur)))))

(defn thing [in-channel out-channel err-channel]
  (go
    (loop []
      (let [county (<! in-channel)
            f (service/submit @service
                              (lookup-state-action county)
                              (+ 850 (rand-int 200)))]
        (when (:rejected? f)
          (println (:rejected-reason f)))
        (recur)))))

(defn run []
  (reset! service (beehive/service "example" 10 90))
  (let [in-channel (async/chan 10)
        out-channel (async/chan 10)
        err-channel (async/chan 10)]
    (thing in-channel out-channel err-channel)
    (handle-success out-channel)
    (handle-error err-channel)
    in-channel))