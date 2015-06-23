(ns uncontended.beehive.patterns
  (:require [uncontended.beehive.compatibility :as c]
            [uncontended.beehive.future :as f])
  (:import (net.uncontended.precipice RejectedActionException
                                      LoadBalancers
                                      Shotgun
                                      ComposedService)
           (net.uncontended.precipice.concurrent ResilientPromise)
           (uncontended.beehive.service CLJServiceImpl)))

(set! *warn-on-reflection* true)

(defn- transform-map [service->context]
  (into {} (map (fn [[k v]] [(.executor ^CLJServiceImpl k) v]) service->context)))

(defprotocol CLJComposedService
  (submit-action [this action-fn timeout-millis])
  (perform-action [this action-fn]))

(deftype CLJLoadBalancer [^ComposedService balancer]
  CLJComposedService
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
        balancer (LoadBalancers/newRoundRobin service->context)]
    (->CLJLoadBalancer balancer)))

(deftype CLJShotgun [^ComposedService shotgun]
  CLJComposedService
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
        shotgun (Shotgun. service->context (int submission-count))]
    (->CLJShotgun shotgun)))