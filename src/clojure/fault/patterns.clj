(ns fault.patterns
  (:require [fault.service :as service]
            [fault.future :as future]
            [fault.future :as f])
  (:import (fault ServiceExecutor ResilientAction RejectedActionException LoadBalancer Pattern ResilientPatternAction)
           (fault.concurrent DefaultResilientPromise ResilientPromise)
           (java.util ArrayList)
           (fault.service CLJServiceImpl)))

(set! *warn-on-reflection* true)

(defn- wrap-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context] (action-fn context))))

(defprotocol ComposedService
  (submit-action [this action-fn timeout-millis])
  (submit-action-map [this key->fn timeout-millis])
  (perform-action [this action-fn])
  (perform-action-map [this key->fn]))

(deftype CLJLoadBalancer [^Pattern balancer]
  ComposedService
  (submit-action [this action-fn timeout-millis]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.promise
                               (.submitAction balancer
                                              (wrap-action-fn action-fn)
                                              timeout-millis)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e)))))
  (submit-action-map [this key->fn timeout-millis]
    (throw (UnsupportedOperationException.
             "Cannot submit action with map with Balancer")))
  (perform-action [this action-fn]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.performAction balancer
                                             (wrap-action-fn action-fn)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e)))))
  (perform-action-map [this key->fn]
    (throw (UnsupportedOperationException.
             "Cannot perform action with map with Balancer"))))

(defn- transform-map [service->context]
  (into {} (map (fn [[k v]] [(.executor ^CLJServiceImpl k) v]) service->context)))

(defn load-balancer [service->context]
  (let [service->context (transform-map service->context)
        balancer (LoadBalancer/roundRobin service->context)]
    (->CLJLoadBalancer balancer)))

(deftype Shotgun [action-count context shotgun-fn]
  ComposedService
  (submit-action [this action-fn timeout-millis]
    (let [^ResilientPromise promise (DefaultResilientPromise.)
          rejects (ArrayList. ^long action-count)]
      (doseq [[key service] (shotgun-fn)
              :let [svc-context (get context key)]]
        (try
          (.submitAction ^ServiceExecutor (:service-executor service)
                         (reify ResilientAction (run [_] (action-fn svc-context)))
                         promise
                         ^long (long timeout-millis))
          (catch RejectedActionException e (.add rejects (.reason e)))))
      (when (not= (count rejects) action-count)
        (future/->CLJResilientFuture promise))))
  (submit-action-map [this key->fn timeout-millis]
    (let [^ResilientPromise promise (DefaultResilientPromise.)
          rejects (ArrayList. ^long action-count)]
      (doseq [[key service] (shotgun-fn)
              :let [fn (get key->fn key)]]
        (try
          (.submitAction ^ServiceExecutor (:service-executor service)
                         (reify ResilientAction (run [_] (fn)))
                         promise
                         ^long (long timeout-millis))
          (catch RejectedActionException e (.add rejects (.reason e)))))
      (when (not= (count rejects) action-count)
        (future/->CLJResilientFuture promise))))
  (perform-action [this action-fn]
    (throw (UnsupportedOperationException. "Cannot perform action with Shotgun")))
  (perform-action-map [this key->fn]
    (throw (UnsupportedOperationException. "Cannot perform action with Shotgun"))))

(defn shotgun [key->service action-count context]
  (let [key-service-tuples (vec key->service)
        service-count (count key->service)
        rand-fn (fn [] (rand-int service-count))]
    (assert (>= service-count action-count))
    (->Shotgun action-count
               context
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
                                (repeatedly rand-fn))))))))