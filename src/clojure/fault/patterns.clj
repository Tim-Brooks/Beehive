(ns fault.patterns
  (:require [fault.core :as core]
            [fault.future :as future])
  (:import (fault ServiceExecutor
                  RejectedActionException
                  MultipleWriterResilientPromise
                  ResilientPromise
                  ResilientAction)))

(set! *warn-on-reflection* true)

(defprotocol ComposedService
  (submit-action [this action-fn timeout-millis])
  (submit-action-map [this key->fn timeout-millis])
  (perform-action [this action-fn])
  (perform-action-map [this action-fn]))

(deftype LoadBalancer [context load-balancer-fn]
  ComposedService
  (submit-action [this action-fn timeout-millis]
    (some (fn [[key service]]
            (try (core/submit-action service
                                     (partial action-fn (get context key))
                                     timeout-millis)
                 (catch RejectedActionException _ nil)))
          (load-balancer-fn)))
  (submit-action-map [this key->fn timeout-millis]
    (some (fn [[key service]]
            (try (core/submit-action service
                                     (get key->fn key)
                                     timeout-millis)
                 (catch RejectedActionException _ nil)))
          (load-balancer-fn)))
  (perform-action [this action-fn]
    (some (fn [[key service]]
            (try
              (core/perform-action service (partial action-fn (get context key)))
              (catch RejectedActionException _ nil)))
          (load-balancer-fn)))
  (perform-action-map [this key->fn]
    (some (fn [[key service]]
            (try (core/perform-action service (get key->fn key))
                 (catch RejectedActionException _ nil)))
          (load-balancer-fn))))

(defn- next-idx [last-idx current]
  (if (<= last-idx current)
    0
    (inc current)))

(defn load-balancer [key->service context]
  (let [service-count (count key->service)
        next-fn (partial next-idx (dec service-count))
        state (atom -1)
        key-service-tuples (vec key->service)]
    (->LoadBalancer
      context
      (fn []
        (let [start-idx (swap! state next-fn)]
          (map #(nth key-service-tuples %)
               (take service-count (iterate next-fn start-idx))))))))

(defn shotgun [key->service action-count]
  (let [key-service-tuples (vec key->service)
        service-count (count key->service)
        rand-fn (fn [] (rand-int service-count))]
    (assert (>= service-count action-count))
    (if (= service-count action-count)
      (fn []
        key-service-tuples)
      (fn []
        (map #(nth key-service-tuples %)
             (reduce (fn [acc i]
                       (let [acc1 (conj! acc i)]
                         (if (= action-count (count acc1))
                           (reduced (persistent! acc1))
                           acc1)))
                     (transient #{})
                     (repeatedly rand-fn)))))))

(defn submit-shotgun-actions [shotgun key->fn timeout-millis]
  (let [^ResilientPromise promise (MultipleWriterResilientPromise.)]
    (doseq [[key service] (shotgun)
            :let [fn (get key->fn key)]]
      (.submitAction ^ServiceExecutor (:service-executor service)
                     (reify ResilientAction (run [_] (fn)))
                     promise
                     timeout-millis))
    (future/->CLJResilientFuture promise)))