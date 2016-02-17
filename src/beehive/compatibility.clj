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
  (:import (net.uncontended.precipice TimeoutableResult Rejected)))

(defn result->clj-result [result-enum]
  (cond
    (identical? TimeoutableResult/SUCCESS result-enum) :success
    (identical? TimeoutableResult/ERROR result-enum) :error
    (identical? TimeoutableResult/TIMEOUT result-enum) :timeout))

(defn clj-result->result [clj-result]
  (cond
    (identical? clj-result :success) TimeoutableResult/SUCCESS
    (identical? clj-result :error) TimeoutableResult/ERROR
    (identical? clj-result :timeout) TimeoutableResult/TIMEOUT))

(defn clj-rejected->rejected [clj-rejected]
  (cond
    (identical? clj-rejected :max-concurrency-level-exceeded)
    Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED

    (identical? clj-rejected :circuit-open)
    Rejected/CIRCUIT_OPEN

    (identical? clj-rejected :all-services-rejected)
    Rejected/ALL_SERVICES_REJECTED))

;(defn wrap-run-pattern-action-fn [action-fn]
;  (reify ResilientPatternAction
;    (run [_ context]
;      (try
;        (let [result (action-fn context)]
;          {:status :success :result result :success? true})
;        (catch ActionTimeoutException _
;          {:status :timeout :timeout? true})
;        (catch Throwable e
;          {:status :error :error e :error? true})))))

;(defn wrap-run-action-fn [action-fn]
;  (reify ResilientAction
;    (run [_]
;      (try
;        (let [result (action-fn)]
;          {:status :success :result result :success? true})
;        (catch ActionTimeoutException _
;          {:status :timeout :timeout? true})
;        (catch Throwable e
;          {:status :error :error e :error? true})))))

