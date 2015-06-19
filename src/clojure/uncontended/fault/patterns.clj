(ns uncontended.fault.patterns
  (:require [uncontended.fault.compatibility :as c]
            [uncontended.fault.future :as f])
  (:import (net.uncontended.fault ServiceExecutor
                                  ResilientAction
                                  RejectedActionException
                                  LoadBalancer
                                  Pattern)
           (net.uncontended.fault.concurrent DefaultResilientPromise
                                             ResilientPromise)
           (uncontended.fault.service CLJServiceImpl)
           (java.util ArrayList)))

(set! *warn-on-reflection* true)

(defprotocol ComposedService
  (submit-action [this action-fn timeout-millis])
  (perform-action [this action-fn]))

(deftype CLJLoadBalancer [^Pattern balancer]
  ComposedService
  (submit-action [this action-fn timeout-millis]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.promise
                               (.submitAction balancer
                                              (c/wrap-pattern-action-fn action-fn)
                                              timeout-millis)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e)))))
  (perform-action [this action-fn]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.performAction balancer
                                             (c/wrap-pattern-action-fn action-fn)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e))))))

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
        (f/->CLJResilientFuture promise))))
  (perform-action [this action-fn]
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