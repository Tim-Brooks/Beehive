;; Copyright 2016 Timothy Brooks
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

(ns beehive.hive
  (:refer-clojure :exclude [promise future name])
  (:require [beehive.enums :as enums]
            [beehive.future :as f]
            [beehive.metrics :as metrics])
  (:import (clojure.lang APersistentMap ILookup)
           (beehive.java ToCLJ)
           (net.uncontended.precipice Completable
                                      Failable
                                      GuardRail
                                      GuardRailBuilder
                                      ResultView)
           (net.uncontended.precipice.concurrent PrecipicePromise)
           (net.uncontended.precipice.factories Asynchronous Synchronous)
           (net.uncontended.precipice.rejected RejectedException)))

(set! *warn-on-reflection* true)

(defprotocol Hive
  (name [this])
  (result-metrics [this])
  (rejected-metrics [this])
  (latency-metrics [this])
  (back-pressure [this]))

(extend-protocol Hive
  APersistentMap
  (beehive-name [this] (:name this))
  (result-metrics [this] (:result-metrics this))
  (rejected-metrics [this] (:rejected-metrics this))
  (latency-metrics [this] (:latency-metrics this))
  (back-pressure [this] (:back-pressure this)))

(deftype BeehiveCompletable [^Completable completable result-key->enum]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :rejected? false
      default)))

(defn add-bp [^GuardRailBuilder builder mechanisms]
  (doseq [back-pressure mechanisms]
    (.addBackPressure builder back-pressure))
  builder)

(defmacro create-back-pressure [rejected-keys metrics & mechanisms]
  (let [{:keys [key->enum-string cpath]} (enums/generate-rejected-enum rejected-keys)
        key->form (into {} (map (fn [[k s]]
                                  [k (enums/enum-form cpath s)])
                                key->enum-string))
        metrics-fn (first metrics)
        metric-fn-args (rest metrics)]
    `{:rejected-key->enum ~key->form
      :rejected-metrics (~metrics-fn ~key->form ~@metric-fn-args)
      :back-pressure [~@(for [form mechanisms]
                          (clojure.walk/postwalk-replace key->form form))]}))

(defmacro results
  [result->success? metrics-seq & latency-metrics-seq]
  (let [{:keys [key->enum-string cpath]} (enums/generate-result-enum
                                           result->success?)
        key->form (into {} (map (fn [[k s]]
                                  [k (enums/enum-form cpath s)])
                                key->enum-string))
        metrics-fn (first metrics-seq)
        metric-fn-args (rest metrics-seq)
        latency-metrics-seq (first latency-metrics-seq)
        latency? (not (empty? latency-metrics-seq))
        latency-metrics-fn (or (first latency-metrics-seq) identity)
        latency-metrics-args (rest latency-metrics-seq)]
    `(cond-> {:result-key->enum ~key->form
              :result-metrics (~metrics-fn ~key->form ~@metric-fn-args)}
             ~latency?
             (assoc :latency-metrics (~latency-metrics-fn
                                       ~key->form
                                       ~@latency-metrics-args)))))

(defn beehive
  ([name results-map] (beehive name results-map nil))
  ([name
    {:keys [result-key->enum result-metrics latency-metrics]}
    {:keys [rejected-key->enum rejected-metrics back-pressure]}]
   (let [rejected-metrics1 (or rejected-metrics (metrics/no-op-metrics))
         builder (-> (GuardRailBuilder.)
                     (.name name)
                     (.resultMetrics (:precipice-metrics (meta result-metrics)))
                     (.rejectedMetrics (:precipice-metrics (meta rejected-metrics1)))
                     (cond->
                       latency-metrics
                       (.resultLatency (:precipice-metrics (meta latency-metrics)))
                       back-pressure
                       (add-bp back-pressure)))]
     (cond-> {:name name
              :result-metrics result-metrics
              :result-key->enum result-key->enum
              :guard-rail (.build ^GuardRailBuilder builder)}
             rejected-metrics
             (assoc :rejected-metrics rejected-metrics)
             rejected-key->enum
             (assoc :rejected-key->enum rejected-key->enum)
             latency-metrics
             (assoc :latency-metrics latency-metrics)
             back-pressure
             (assoc :back-pressure back-pressure)))))

(defn completable
  "Takes a context that was recieved when permits were acquired. And returns
  a completable. The completable is internally wired to release permits and
  update metrics upon completion.

  The completable is not thread-safe and cannot be written to from multiple
  threads. If you would like a thread-safe alternative you should use a promise."
  [context]
  (if (:rejected? context)
    context
    (try
      (let [{:keys [guard-rail result-key->enum]} (:beehive (meta context))]
        (->BeehiveCompletable
          (Synchronous/getCompletable
            guard-rail
            (:permit-count context)
            (:start-nanos context))
          result-key->enum))
      (catch RejectedException e
        {:rejected? true :rejected-reason (.keyword ^ToCLJ (.reason e))}))))

(defn promise
  "Takes a context that was recieved when permits were acquired. And returns
  a promise. The promise is internally wired to release permits and
  update metrics upon completion.

  The promise is thread-safe and can be written to by multiple threads."
  [context]
  (if (:rejected? context)
    context
    (try
      (let [{:keys [guard-rail result-key->enum]} (:beehive (meta context))]
        (->BeehiveCompletable
          (Asynchronous/getPromise
            guard-rail
            (:permit-count context)
            (:start-nanos context))
          result-key->enum))
      (catch RejectedException e
        {:rejected? true :rejected-reason (.keyword ^ToCLJ (.reason e))}))))

(defn complete!
  "Completes the supplied completable with the result and the value provided. The
  result is constrained to the results associated with the beehive that created
  this completable. An invalid result will cause an exception to be thrown."
  [^BeehiveCompletable completable result value]
  (if-let [^Failable result-enum (get (.-result_key__GT_enum completable) result)]
    (let [^Completable java-c (.-completable completable)]
      (if (.isSuccess result-enum)
        (.complete java-c result-enum value)
        (.completeExceptionally java-c result-enum value)))
    (throw (IllegalArgumentException.
             (format "Invalid result '%s'; Valid results are '%s'"
                     result
                     (keys (.-result_key__GT_enum completable)))))))

(defn to-future
  "Returns a future of the values contained in a promise.

  If this is called with a rejection map, then the rejection map will be returned."
  [promise]
  (if (:rejected? promise)
    (f/rejected-future (:rejected-reason promise))
    (let [precipice-completable (.-completable ^BeehiveCompletable promise)
          java-f (.future ^PrecipicePromise precipice-completable)]
      (f/->BeehiveFuture java-f))))

(defn to-result-view
  "Returns a result map of the values contained in a completable.

  If this is called with a rejection map, then the rejection map will be returned."
  [completable]
  (if (:rejected? completable)
    completable
    (let [^Completable c (.-completable ^BeehiveCompletable completable)
          ^ResultView r (.resultView c)
          result (.getResult r)]
      (cond
        (nil? result)
        {:pending? true :rejected? false}
        (.isSuccess result)
        {:success? true :value (.getValue r)
         :result (.keyword ^ToCLJ (.getResult r)) :failure? false}
        :else
        {:success? false :value (.getError r)
         :result (.keyword ^ToCLJ (.getResult r)) :failure? true}))))

(defn release-raw-permits
  "Releases a raw permit count. This call would allows multiple calls to acquire
  to be released in one call. Since this call will simply release x number of
  permits, no metrics will be updated. Generally it is preferable to call
  release-without-result or release for each context returned by successful calls
  to acquire."
  ([beehive permit-count]
   (let [nano-time (.nanoTime (.getClock ^GuardRail (:guard-rail beehive)))]
     (release-raw-permits beehive permit-count nano-time)))
  ([beehive permit-count nano-time]
   (let [^GuardRail guard-rail (:guard-rail beehive)]
     (.releasePermitsWithoutResult guard-rail permit-count nano-time))))

(defn release-without-result
  "Releases permits with out considering the result. This means that result
  metrics and latency will not be updated. The caller should pass the context
  map returned by a successful acquire call. The map should contain the
  start-nanos and permit-count.
  `{:start-nanos 12973909840390 :permit-count 2}`

  If the context map lacks the permit-count key, this function will
  not do anything."
  ([beehive context]
   (release-without-result
     beehive context (.nanoTime (.getClock ^GuardRail (:guard-rail beehive)))))
  ([beehive {:keys [permit-count] :as context} nano-time]
   (when permit-count
     (release-raw-permits beehive permit-count nano-time))))

(defn release
  "Releases permits and updates metrics with the result. The caller should pass
  the context map returned by a successful acquire call. The map should contain the
  start-nanos and permit-count.
  `{:start-nanos 12973909840390 :permit-count 2}`

  If the context map lacks the permit-count key, this function will
  not do anything."
  ([beehive context result]
   (let [nano-time (.nanoTime (.getClock ^GuardRail (:guard-rail beehive)))]
     (release beehive context result nano-time)))
  ([beehive {:keys [permit-count start-nanos] :as context} result nano-time]
   (when permit-count
     (let [^GuardRail guard-rail (:guard-rail beehive)
           result-enum (get (:result-key->enum beehive) result)]
       (.releasePermits
         guard-rail permit-count result-enum start-nanos nano-time)))))

(defn acquire
  "Attempts to acquire requested permits. Permits will be successfully acquired
  if none of the back pressure mechanisms trigger a rejection.

  If the attempt is successful, a map with start time in nanoseconds and the
  number of permits will be returned.
  `{:start-nanos 12973909840390 :permit-count 2}`

  If the attempt fails, a map with the reason will be returned.
  `{:rejected? true :rejected-reason :max-concurrency-level-violated}`"
  ([beehive] (acquire beehive 1))
  ([{:keys [guard-rail] :as beehive} permits]
   (acquire beehive permits (.nanoTime (.getClock ^GuardRail guard-rail))))
  ([beehive permits nano-time]
   (let [^GuardRail guard-rail (:guard-rail beehive)]
     (if-let [rejected-reason (.acquirePermits ^GuardRail guard-rail permits nano-time)]
       {:rejected? true :rejected-reason (.keyword ^ToCLJ rejected-reason)}
       (with-meta {:start-nanos nano-time :permit-count permits}
                  {:beehive beehive})))))

(defn acquire-promise
  "Attempts to acquire requested permits. If the permits are acquired, a promise
  that can be completed is returned. The promise is internally wired to release
  permits and update metrics upon completion.

  The promise is thread-safe and can be written to by multiple threads.

  If the permits cannot be acquired, a map with the reason will be returned.
  `{:rejected? true :reason :max-concurrency-level-violated}"
  ([beehive] (acquire-promise beehive 1))
  ([beehive permits]
   (promise (acquire beehive permits))))

(defn acquire-completable
  "Attempts to acquire requested permits. If the permits are acquired, a
  completable that can be completed is returned. The completable is internally
  wired to release permits and update metrics upon completion.

  The completable is not thread-safe and cannot be written to from multiple
  threads. If you would like a thread-safe alternative you should use a promise.

  If the permits cannot be acquired, a map with the reason will be returned.
  `{:rejected? true :reason :max-concurrency-level-violated}"
  ([beehive] (acquire-completable beehive 1))
  ([beehive permits]
   (completable (acquire beehive permits))))