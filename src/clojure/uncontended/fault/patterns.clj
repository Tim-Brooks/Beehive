(ns uncontended.fault.patterns
  (:require [uncontended.fault.compatibility :as c]
            [uncontended.fault.future :as f])
  (:import (net.uncontended.fault RejectedActionException
                                  LoadBalancer
                                  Pattern ShotgunPattern)
           (net.uncontended.fault.concurrent ResilientPromise)
           (uncontended.fault.service CLJServiceImpl)))

(set! *warn-on-reflection* true)

(defn- transform-map [service->context]
  (into {} (map (fn [[k v]] [(.executor ^CLJServiceImpl k) v]) service->context)))

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

(defn load-balancer [service->context]
  (let [service->context (transform-map service->context)
        balancer (LoadBalancer/roundRobin service->context)]
    (->CLJLoadBalancer balancer)))

(deftype CLJShotgun [^Pattern shotgun]
  ComposedService
  (submit-action [this action-fn timeout-millis]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.promise
                               (.submitAction shotgun
                                              (c/wrap-pattern-action-fn action-fn)
                                              timeout-millis)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e)))))

  (perform-action [this action-fn]
    (throw (UnsupportedOperationException. "Cannot perform action with Shotgun"))))

(defn shotgun [service->context submission-count]
  (let [service->context (transform-map service->context)
        shotgun (ShotgunPattern. service->context (int submission-count))]
    (->CLJShotgun shotgun)))