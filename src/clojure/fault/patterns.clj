(ns fault.patterns
  (:require [fault.core :as core])
  (:import (fault ServiceExecutor)))

(set! *warn-on-reflection* true)

(defn- next-service [last-idx current]
  (if (<= last-idx current)
    0
    (inc current)))

(defn load-balancer [key->service]
  (let [next-fn (partial next-service (dec (count key->service)))
        state (atom -1)
        key-service-tuples (vec key->service)]
    (fn []
      (nth key-service-tuples (swap! state next-fn)))))

(defn submit-load-balanced-action [load-balancer key->fn timeout-millis]
  (let [[key {:keys [service]}] (load-balancer)]
    (core/submit-action ^ServiceExecutor service
                        (get key->fn key)
                        timeout-millis)))

(defn perform-load-balanced-action [load-balancer key->fn]
  (let [[key {:keys [service]}] (load-balancer)]
    (core/perform-action service (get key->fn key))))