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
            [beehive.future :as f])
  (:import (clojure.lang APersistentMap ILookup IPending IDeref)
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
  (result-counts [this])
  (rejected-counts [this])
  (result-latency [this])
  (back-pressure [this]))

(extend-protocol Hive
  APersistentMap
  (name [this] (:name this))
  (result-counts [this] (:result-counts this))
  (rejected-counts [this] (:rejected-counts this))
  (result-latency [this] (:result-latency this))
  (back-pressure [this] (:back-pressure this)))

(deftype BeehiveCompletable [^Completable completable result-key->enum]
  IDeref
  (deref [this]
    (.getResult (.resultView completable)))
  IPending
  (isRealized [_]
    (not (nil? (.getResult (.resultView completable)))))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key default]
    (case key
      :result (enums/enum->keyword (.getResult (.resultView completable)))
      :pending? (not (.isRealized this))
      :cancelled? false
      :rejected? false
      :success? (f/success? (.resultView completable))
      :failure? (not (f/success? (.resultView completable)))
      :value (or (.getValue (.resultView completable))
                 (.getError (.resultView completable)))
      default)))

(defn- to-name [k]
  (if (keyword? k)
    (clojure.core/name k)
    (str k)))

(defn- ^String compile-msg [msg]
  (str msg " in " '*ns* ":" (:line (meta '&form))))

(defn- h-assert [assertion msg]
  (when-not assertion
    (throw (IllegalArgumentException.
             (compile-msg msg)))))

(defn- run-assertions [bindings]
  (h-assert (vector? bindings) "lett requires a vector for its binding")
  (h-assert (even? (count bindings)) "lett requires an even number of forms in binding vector")
  (h-assert (>= 4 (count bindings)) "lett allows a maximum of four forms in binding vector")
  (let [symbols (take-nth 2 bindings)]
    (doseq [sym symbols]
      (h-assert (symbol? sym)
                (str "Non-symbol binding form: " sym))))
  (let [map&set (take-nth 2 (rest bindings))]
    (assert (every? (fn [x] (or (map? x) (set? x))) map&set))
    (assert (>= 1 (->> map&set
                       (filter map?)
                       count)))
    (assert (>= 1 (->> map&set
                       (filter set?)
                       count)))))

(defn- replace-bindings [bindings]
  (mapv (fn [x]
          (cond (map? x) (enums/generate-result-class x)
                (set? x) (enums/generate-rejected-class x)
                :else x))
        bindings))

(defn- replace-keywords [body keyword->enum]
  (clojure.walk/postwalk-replace keyword->enum body))

(defn- result-counts1 [^GuardRailBuilder builder result-metrics]
  (.resultCounts builder (:precipice-metrics result-metrics))
  builder)

(defn- result-latency1 [^GuardRailBuilder builder result-latency]
  (.resultLatency builder (:precipice-metrics result-latency))
  builder)

(defn- rejected-counts1 [^GuardRailBuilder builder rejected-metrics]
  (.rejectedCounts builder (:precipice-metrics rejected-metrics))
  builder)

(defn- add-bp1 [^GuardRailBuilder builder mechanisms]
  (doseq [[k back-pressure] mechanisms]
    (.addBackPressure builder (to-name k) back-pressure))
  builder)

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

(defn map->hive
  "Turns a base beehive map into an actual beehive implementation."
  [{:keys [name
           result-counts
           result-latency
           back-pressure
           rejected-counts]
    :as hive-map}]
  (let [builder (-> (GuardRailBuilder.)
                    (.name name)
                    (result-counts1 result-counts)
                    (result-latency1 result-latency)
                    (rejected-counts1 rejected-counts)
                    (add-bp1 back-pressure))]
    (assoc hive-map
      :result-counts result-counts
      :result-latency result-latency
      :rejected-counts rejected-counts
      :back-pressure (or back-pressure [])
      :guard-rail (.build ^GuardRailBuilder builder))))

(defmacro lett [bindings & body]
  (run-assertions bindings)
  (let [bindings (replace-bindings bindings)
        key->form (->> (rest bindings)
                       (take-nth 2)
                       (map enums/enum-class-to-keyword->form)
                       (apply merge))]
    `(let ~bindings
       ~@(replace-keywords body key->form))))

(defn set-result-counts [hive result-counts]
  "Sets the result counts for this beehive."
  (assoc hive :result-counts result-counts))

(defn set-rejected-counts [hive rejected-counts]
  "Sets the rejected counts for this beehive."
  (assoc hive :rejected-counts rejected-counts))

(defn set-result-latency [hive result-latency]
  "Sets the result latency metrics for this beehive."
  (assoc hive :result-latency result-latency))

(defn add-backpressure [hive k back-pressure]
  "Adds a backpressure mechanism to this beehive."
  (assoc-in hive [:back-pressure k] back-pressure))

(defn beehive [name result-class rejected-class]
  "Returns a base map indicating a beehive name and result/rejected types."
  {:name name
   :result-key->enum (enums/enum-class-to-keyword->enum result-class)
   :rejected-key->enum (enums/enum-class-to-keyword->enum rejected-class)})
