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
  (:require [beehive.compatibility :as c])
  (:import (clojure.lang IDeref IBlockingDeref IPending ILookup)
           (java.util.concurrent TimeUnit)
           (net.uncontended.precipice TimeoutableResult PrecipiceFunction RejectedException Rejected)
           (net.uncontended.precipice.concurrent PrecipiceFuture)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (identical? TimeoutableResult/SUCCESS status-enum) :success
    (identical? TimeoutableResult/ERROR status-enum) :error
    (identical? TimeoutableResult/TIMEOUT status-enum) :timeout))

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
    (not (nil? (.getStatus future))))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :status (or (status (.getStatus future)) :pending)
      :success? (identical? TimeoutableResult/SUCCESS (.getStatus future))
      :timeout? (identical? TimeoutableResult/TIMEOUT (.getStatus future))
      :error? (identical? TimeoutableResult/ERROR (.getStatus future))
      :pending? (not (.isRealized this))
      :cancelled? (.isCancelled future)
      :rejected? false
      :result (.getResult future)
      :error (.getError future)
      default)))

(deftype BeehiveRejectedFuture [^RejectedException ex reason]
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
  {Rejected/CIRCUIT_OPEN :circuit-open
   Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED :max-concurrency-level-exceeded
   Rejected/ALL_SERVICES_REJECTED :all-services-rejected})

(defn rejected-action-future [^RejectedException ex]
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
  (apply [this status result]
    (fn (:status future) result)))

(defn on-complete [f function]
  (if (instance? BeehiveRejectedFuture f)
    (function :rejected (.reason ^BeehiveRejectedFuture f))
    (let [^PrecipiceFuture java-f (.future ^BeehiveFuture f)
          cb (CLJCallback. f function)]
      (.onSuccess java-f cb)
      (.onError java-f cb))))