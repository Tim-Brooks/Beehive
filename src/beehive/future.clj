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
  (:refer-clojure :exclude [await])
  (:import (clojure.lang IDeref IBlockingDeref IPending ILookup)
           (java.util.concurrent TimeUnit)
           (net.uncontended.precipice Status RejectionReason PrecipiceFunction RejectedActionException)
           (net.uncontended.precipice.concurrent PrecipiceFuture)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (identical? Status/PENDING status-enum) :pending
    (identical? Status/SUCCESS status-enum) :success
    (identical? Status/ERROR status-enum) :error
    (identical? Status/TIMEOUT status-enum) :timeout
    (identical? Status/CANCELLED status-enum) :cancelled))

(deftype BeehiveFuture [^PrecipiceFuture future]
  IDeref
  (deref [this]
    (.get future))
  IBlockingDeref
  (deref [this timeout-ms timeout-val]
    (if (.await future timeout-ms TimeUnit/MILLISECONDS)
      (.get future)
      timeout-val))
  IPending
  (isRealized [_]
    (not (identical? Status/PENDING (.getStatus future))))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status (status (.getStatus future))
      :success? (identical? Status/SUCCESS (.getStatus future))
      :timeout? (identical? Status/TIMEOUT (.getStatus future))
      :error? (identical? Status/ERROR (.getStatus future))
      :pending? (identical? Status/PENDING (.getStatus future))
      :cancelled? (identical? Status/CANCELLED (.getStatus future))
      :rejected? false
      :result (.result future)
      :error (.error future)
      default)))

(deftype BeehiveRejectedFuture [^RejectedActionException ex reason]
  IDeref
  (deref [this] (throw ex))
  IBlockingDeref
  (deref [this timeout-ms timeout-val] (throw ex))
  IPending
  (isRealized [_]
    true)
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status :rejected
      :rejected? true
      :success? false
      :timeout? false
      :error? false
      :pending? false
      :cancelled? false
      :rejected-reason reason
      default)))

(def ^:private reject-enum->keyword
  {RejectionReason/CIRCUIT_OPEN :circuit-open
   RejectionReason/MAX_CONCURRENCY_LEVEL_EXCEEDED :max-concurrency-level-exceeded
   RejectionReason/QUEUE_FULL :queue-full
   RejectionReason/SERVICE_SHUTDOWN :service-shutdown
   RejectionReason/ALL_SERVICES_REJECTED :all-services-rejected})

(defn rejected-action-future [^RejectedActionException ex]
  (->BeehiveRejectedFuture ex (get reject-enum->keyword (.reason ex))))

(defn cancel! [f]
  (when (instance? BeehiveFuture f)
    (.cancel ^PrecipiceFuture (.future ^BeehiveFuture f) true)))

(defn await
  ([f]
   (when (instance? BeehiveFuture f)
     (.await ^PrecipiceFuture (.future ^BeehiveFuture f))))
  ([f timeout-ms]
   (when (instance? BeehiveFuture f)
     (.await ^PrecipiceFuture (.future ^BeehiveFuture f) timeout-ms TimeUnit/MILLISECONDS))))

(deftype CLJCallback [^BeehiveFuture future fn]
  PrecipiceFunction
  (apply [this result]
    (fn (:status future) result)))

(defn on-complete [f function]
  (if (instance? BeehiveRejectedFuture f)
    (function :rejected (.reason ^BeehiveRejectedFuture f))
    (let [^PrecipiceFuture java-f (.future ^BeehiveFuture f)
          cb (CLJCallback. f function)]
      (.onSuccess java-f cb)
      (.onError java-f cb)
      (.onTimeout java-f cb))))