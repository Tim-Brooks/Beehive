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

(ns beehive.future
  (:import (net.uncontended.precipice Status RejectionReason)
           (net.uncontended.precipice.concurrent ResilientPromise)
           (clojure.lang IDeref IBlockingDeref IPending ILookup)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (= Status/PENDING status-enum) :pending
    (= Status/SUCCESS status-enum) :success
    (= Status/ERROR status-enum) :error
    (= Status/TIMEOUT status-enum) :timeout))

(deftype CLJResilientFuture [^ResilientPromise promise]
  IDeref
  (deref [this] (or (.awaitResult promise) (.getError promise) (:status this)))
  IBlockingDeref
  (deref
    [this timeout-ms timeout-val]
    (if (.await promise timeout-ms TimeUnit/MILLISECONDS)
      (or (.getResult promise) (.getError promise) (:status this))
      timeout-val))
  IPending
  (isRealized [_]
    (.isDone promise))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status (status (.getStatus promise))
      :success? (identical? :success (status (.getStatus promise)))
      :timeout? (identical? :timeout (status (.getStatus promise)))
      :error? (identical? :error (status (.getStatus promise)))
      :pending? (identical? :pending (status (.getStatus promise)))
      :result (.getResult promise)
      :error (.getError promise)
      :rejected? false
      default)))

(deftype CLJRejectedFuture [reason]
  IDeref
  (deref [this] reason)
  IBlockingDeref
  (deref [this timeout-ms timeout-val] reason)
  IPending
  (isRealized [_]
    true)
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status :rejected
      :rejected? true
      :rejected-reason reason
      default)))

(def ^:private reject-enum->keyword
  {RejectionReason/CIRCUIT_OPEN :circuit-open
   RejectionReason/MAX_CONCURRENCY_LEVEL_EXCEEDED :max-concurrency-level-exceeded
   RejectionReason/QUEUE_FULL :queue-full
   RejectionReason/SERVICE_SHUTDOWN :service-shutdown
   RejectionReason/ALL_SERVICES_REJECTED :all-services-rejected})

(defn rejected-action-future [reason]
  (->CLJRejectedFuture (get reject-enum->keyword reason)))