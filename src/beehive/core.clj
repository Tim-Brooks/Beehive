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

(ns beehive.core
  (:require [beehive.service :as s]))

(set! *warn-on-reflection* true)
(await)
(when
  (try
    (require '[clojure.core.async])
    true
    (catch Exception _
      false))
  (do (require '[beehive.async])))

(defn service
  [name pool-size max-concurrency
   & {:keys [breaker metrics]
      :or {breaker {} metrics {:slots-to-track 3600
                               :resolution 1
                               :time-unit :seconds}}}]
  (s/service name pool-size max-concurrency breaker metrics))

(defn submit-action [service f time-out-ms]
  (s/submit service f time-out-ms))

