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
  (:require [beehive.enums :as enums])
  (:import (clojure.lang IDeref IBlockingDeref IPending ILookup)
           (java.util.concurrent TimeUnit)
           (net.uncontended.precipice PrecipiceFunction)
           (net.uncontended.precipice.concurrent Eventual)
           (net.uncontended.precipice.rejected RejectedException)))

(set! *warn-on-reflection* true)

(defn- success? [^Eventual eventual]
  (if-let [status (.getStatus eventual)]
    (.isSuccess status)
    false))

(deftype BeehiveFuture [^Eventual eventual]
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
      :result (enums/enum->keyword (.getStatus eventual))
      :pending? (not (.isRealized this))
      :cancelled? (.isCancelled eventual)
      :rejected? false
      :success? (success? eventual)
      :failure? (not (success? eventual))
      :value (or (.getResult eventual) (.getError eventual))
      default)))

(deftype BeehiveRejectedFuture [reason]
  IDeref
  (deref [this] (throw (RejectedException. reason)))
  IBlockingDeref
  (deref [this timeout-ms timeout-val] (throw (RejectedException. reason)))
  IPending
  (isRealized [_] true)
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :rejected? true
      :rejected-reason reason
      :pending? false
      :cancelled? false
      default)))

(defn rejected-future [reason]
  (->BeehiveRejectedFuture reason))

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
  (apply [this _ _]
    (if (:success? future)
      (fn {:success? true
           :value (:value future)
           :result (:result future)
           :failure? false})
      (fn {:failure? true
           :value (:value future)
           :result (:result future)
           :success? false}))))

(defn on-complete [f function]
  (if (:rejected? f)
    (function {:rejected? true :rejected-reason (:rejected-reason f)})
    (let [^Eventual java-f (.eventual ^BeehiveFuture f)
          cb (CLJCallback. f function)]
      (.onSuccess java-f cb)
      (.onError java-f cb))))