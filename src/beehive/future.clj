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
           (net.uncontended.precipice PrecipiceFunction)
           (net.uncontended.precipice.concurrent  Eventual)
           (net.uncontended.precipice.rejected RejectedException)
           (beehive.enums ToCLJ)))

(set! *warn-on-reflection* true)

(deftype BeehiveFuture [^Eventual eventual status-fn]
  IDeref
  (deref [this]
    (.get eventual))
  IBlockingDeref
  (deref [this timeout-ms timeout-val]
    (if (.await eventual timeout-ms TimeUnit/MILLISECONDS)
      (.get eventual)
      timeout-val))
  IPending
  (isRealized [_]
    (not (nil? (.getStatus eventual))))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :status (or (status-fn (.getStatus eventual)) :pending)
      :pending? (not (.isRealized this))
      :cancelled? (.isCancelled eventual)
      :rejected? false
      :result (.getResult eventual)
      :error (.getError eventual)
      default)))

(deftype BeehiveRejectedFuture [^RejectedException ex reason]
  IDeref
  (deref [this] (throw ex))
  IBlockingDeref
  (deref [this timeout-ms timeout-val] (throw ex))
  IPending
  (isRealized [_] true)
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

(defn rejected-future [^RejectedException ex]
  (->BeehiveRejectedFuture ex (.keyword ^ToCLJ (.reason ex))))

(defn cancel! [f]
  (when (instance? BeehiveFuture f)
    (.cancel ^Eventual (.eventual ^BeehiveFuture f) true)))

(defn await!
  ([f]
   (when (instance? BeehiveFuture f)
     (.await ^Eventual (.eventual ^BeehiveFuture f))))
  ([f timeout-ms]
   (when (instance? BeehiveFuture f)
     (.await ^Eventual (.eventual ^BeehiveFuture f)
             timeout-ms
             TimeUnit/MILLISECONDS))))

(deftype CLJCallback [^BeehiveFuture future fn]
  PrecipiceFunction
  (apply [this status result]
    (fn (:status future) result)))

(defn on-complete [f function]
  (if (:rejected? f)
    (function :rejected (:rejected-reason f))
    (let [^Eventual java-f (.eventual ^BeehiveFuture f)
          cb (CLJCallback. f function)]
      (.onSuccess java-f cb)
      (.onError java-f cb))))