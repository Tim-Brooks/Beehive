;; Copyright 2014 Timothy Brooks
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

(ns beehive.patterns
  (:require [beehive.compatibility :as c]
            [beehive.future :as f])
  (:import (beehive.service CLJServiceImpl)
           (net.uncontended.precipice RejectedActionException)
           (net.uncontended.precipice.pattern MultiLoadBalancer
                                              Shotgun
                                              LoadBalancers)))

(set! *warn-on-reflection* true)

(defn- transform-map [service->context]
  (into {} (map (fn [[k v]] [(.service ^CLJServiceImpl k) v]) service->context)))

(defprotocol CLJAsyncPattern
  (submit-action [this action-fn timeout-millis]))

(defprotocol CLJSyncPattern
  (run-action [this action-fn]))

(deftype CLJLoadBalancer [^MultiLoadBalancer balancer]
  CLJAsyncPattern
  (submit-action [_ action-fn timeout-millis]
    (try (f/->BeehiveFuture
           (.submit balancer
                    (c/wrap-pattern-action-fn action-fn)
                    timeout-millis))
         (catch RejectedActionException e
           (f/rejected-action-future e))))
  CLJSyncPattern
  (run-action [_ action-fn]
    (try
      (.run balancer (c/wrap-run-pattern-action-fn action-fn))
      (catch RejectedActionException e
        {:status :rejected
         :rejected? true
         :rejected-reason (c/rejected-exception->reason e)}))))

(defn load-balancer [service->context]
  (let [service->context (transform-map service->context)
        balancer (LoadBalancers/multiRoundRobin service->context)]
    (->CLJLoadBalancer balancer)))

(deftype CLJShotgun [^Shotgun shotgun]
  CLJAsyncPattern
  (submit-action [_ action-fn timeout-millis]
    (try (f/->BeehiveFuture
           (.submit shotgun
                    (c/wrap-pattern-action-fn action-fn)
                    timeout-millis))
         (catch RejectedActionException e
           (f/rejected-action-future e)))))

(defn shotgun [service->context submission-count]
  (let [service->context (transform-map service->context)
        shotgun (Shotgun. service->context (int submission-count))]
    (->CLJShotgun shotgun)))