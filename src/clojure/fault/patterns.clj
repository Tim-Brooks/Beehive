(ns fault.patterns
  (:require [fault.core :as core])
  (:import (fault ServiceExecutor)))

(set! *warn-on-reflection* true)

(defn- next-idx [last-idx current]
  (if (<= last-idx current)
    0
    (inc current)))

(defn- next-service-seq [next-fn current]
  (lazy-seq
    (cons current
          (next-service-seq next-fn (next-fn current)))))

(defn load-balancer [key->service]
  (let [next-fn (partial next-idx (dec (count key->service)))
        state (atom -1)
        key-service-tuples (vec key->service)]
    (fn []
      (let [start-idx (swap! state next-fn)]
        (map #(nth key-service-tuples %) (take (count key->service)
                                               (next-service-seq next-fn
                                                                 start-idx)))))))

(defn submit-load-balanced-action [load-balancer key->fn timeout-millis]
  (let [[key {:keys [service]}] (load-balancer)]
    (core/submit-action ^ServiceExecutor service
                        (get key->fn key)
                        timeout-millis)))

(defn perform-load-balanced-action [load-balancer key->fn]
  (let [[key {:keys [service]}] (load-balancer)]
    (core/perform-action service (get key->fn key))))

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