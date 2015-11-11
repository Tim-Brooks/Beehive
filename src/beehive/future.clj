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
  (:import (clojure.lang IDeref IBlockingDeref IPending ILookup)
           (java.util.concurrent TimeUnit)
           (net.uncontended.precipice Status RejectionReason PrecipiceFunction)
           (net.uncontended.precipice.concurrent PrecipiceFuture)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (= Status/PENDING status-enum) :pending
    (= Status/SUCCESS status-enum) :success
    (= Status/ERROR status-enum) :error
    (= Status/TIMEOUT status-enum) :timeout
    (= Status/CANCELLED status-enum) :cancelled))

(deftype CLJResilientFuture [^PrecipiceFuture future]
  IDeref
  (deref [this]
    (do (.await future)
        (or (.result future) (.error future) (:status this))))
  IBlockingDeref
  (deref
    [this timeout-ms timeout-val]
    (if (.await future timeout-ms TimeUnit/MILLISECONDS)
      (or (.result future) (.error future) (:status this))
      timeout-val))
  IPending
  (isRealized [_]
    (not= Status/PENDING (.getStatus future)))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status (status (.getStatus future))
      :success? (identical? :success (status (.getStatus future)))
      :timeout? (identical? :timeout (status (.getStatus future)))
      :error? (identical? :error (status (.getStatus future)))
      :pending? (identical? :pending (status (.getStatus future)))
      :cancelled? (identical? :cancelled (status (.getStatus future)))
      :result (.result future)
      :error (.error future)
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

(defn cancel [^CLJResilientFuture f]
  (.cancel ^PrecipiceFuture (.future f) true))

(deftype CLJCallback [^PrecipiceFuture future fn]
  PrecipiceFunction
  (apply [this result]
    (fn (:status future) result)))

(defn on-complete [^CLJResilientFuture f fn]
  (let [^PrecipiceFuture java-f (.future f)
        cb (CLJCallback. java-f fn)]
    (.onSuccess java-f cb)
    (.onError java-f cb)
    (.onTimeout java-f cb)))