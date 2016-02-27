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

(ns beehive.guard-rail
  (:import (net.uncontended.precipice GuardRailBuilder)
           (net.uncontended.precipice.rejected Rejected)
           (beehive.generator EnumBuilder)
           (net.uncontended.precipice.result TimeoutableResult)))

(set! *warn-on-reflection* true)

(defn- do-assertions [clauses]
  (assert (even? (count clauses)))
  (assert (every? keyword? (take-nth 2 clauses)))
  (assert (every? seq? (take-nth 2 (rest clauses)))))

(defn- do-new-assertions [clauses]
  (assert (every? keyword? (keys clauses)))
  (assert (every? seq? (vals (rest clauses)))))

(defn to-enum-string [k]
  (.toUpperCase (.replace (name k) \- \_)))

(defn gen-fn [ks]
  (into {} (mapv (fn [k] [k (symbol (to-enum-string k))]) ks)))

(defmacro backpressure [builder & clauses]
  (do-assertions clauses)
  (let [ks (set (take-nth 2 clauses))
        cpath (EnumBuilder/buildRejectedEnum (mapv to-enum-string ks))
        cpath (symbol cpath)
        key->enum (gen-fn ks)
        clauses (partition 2 clauses)
        b (gensym)]
    `(let [~b ~builder]
       ~@(map (fn [[reason bp-fn-seq]]
                `(.addBackPressure ~b (~(first bp-fn-seq)
                                        ~@(rest bp-fn-seq)
                                        (. ~cpath ~(get key->enum reason)))))
              clauses)
       ~b)))

(defmacro new-bp [builder reason->backpressure]
  (do-new-assertions reason->backpressure)
  (let [ks (set (keys reason->backpressure))
        cpath (EnumBuilder/buildRejectedEnum (mapv to-enum-string ks))
        cpath (symbol cpath)
        key->enum (gen-fn ks)
        b (gensym)]
    `(let [~b ~builder]
       ~@(map (fn [[reason bp-fn-seq]]
                `(.addBackPressure ~b (~(first bp-fn-seq)
                                        ~@(rest bp-fn-seq)
                                        (. ~cpath ~(get key->enum reason)))))
              reason->backpressure)
       ~b)))

(defn result-enum-string [k s?]
  (str (to-enum-string k) (when-not s? "_F")))

(defn gen-type [result->success?]
  (let [key->enum-string (into {} (map (fn [[k s?]]
                                         [k (symbol (result-enum-string k s?))])
                                       result->success?))
        cpath (EnumBuilder/buildResultEnum (map str (vals key->enum-string)))
        cpath (symbol cpath)]
    {:cpath cpath
     :key->enum-string key->enum-string}))

(defmacro create-types [result->success?]
  (let [{:keys [key->enum-string cpath]} (gen-type result->success?)]
    `(do
       (-> {}
           ~@(map (fn [[k es]]
                    (list assoc k `(. ~cpath ~es)))
                  key->enum-string)))))

(defmacro guard-rail
  [name result-metrics rejected-metrics &
   {:keys [latency-metrics backpressure result->success?]}]
  (let [rm-fn (first result-metrics)
        rj-fn (first rejected-metrics)
        rm-args (rest result-metrics)
        rj-args (rest rejected-metrics)
        result-type TimeoutableResult                       ;; Use correct type
        rejected-type Rejected                              ;; Use correct type
        l-fn (first latency-metrics)
        l-args (rest latency-metrics)]
    {:pr `(-> (GuardRailBuilder.)
             (.name ~name)
             (.resultMetrics (~rm-fn ~result-type ~@rm-args))
             (.rejectedMetrics (~rj-fn ~rejected-type ~@rj-args))
             (cond->
               ~latency-metrics
               (.resultLatency (~l-fn ~result-type ~@l-args))
               ~backpressure
               (new-bp ~backpressure)))
     :pl `(create-types ~result->success?)}))




