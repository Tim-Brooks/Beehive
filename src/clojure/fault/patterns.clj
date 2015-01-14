(ns fault.patterns
  (:require [fault.core :as core]
            [fault.future :as future])
  (:import (fault ServiceExecutor
                  RejectedActionException
                  MultipleWriterResilientPromise
                  ResilientPromise
                  ResilientAction ResilientFuture)))

(set! *warn-on-reflection* true)

(defn- next-idx [last-idx current]
  (if (<= last-idx current)
    0
    (inc current)))

(defn load-balancer [key->service]
  (let [next-fn (partial next-idx (dec (count key->service)))
        state (atom -1)
        key-service-tuples (vec key->service)]
    (fn []
      (let [start-idx (swap! state next-fn)]
        (map #(nth key-service-tuples %) (take (count key->service)
                                               (iterate next-fn start-idx)))))))

(defn submit-load-balanced-action [load-balancer key->fn timeout-millis]
  (some (fn [[key service]]
          (try (core/submit-action service
                                   (get key->fn key)
                                   timeout-millis)
               (catch RejectedActionException _ nil)))
        (load-balancer)))

(defn perform-load-balanced-action [load-balancer key->fn]
  (some (fn [[key service]]
          (try (core/perform-action service (get key->fn key))
               (catch RejectedActionException _ nil)))
        (load-balancer)))

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