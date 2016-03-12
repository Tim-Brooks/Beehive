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
  (:import (net.uncontended.precipice Precipice)
           (net.uncontended.precipice.pattern Pattern
                                              PatternStrategy
                                              RoundRobinLoadBalancer
                                              Shotgun)))

(set! *warn-on-reflection* true)

(deftype BeehivePrecipice [beehive]
  Precipice
  (guardRail [this] (:guard-rail beehive)))

(defn pattern-seq
  ([pattern] (pattern-seq pattern 1))
  ([pattern permit-count]
   (let [^Pattern pattern pattern]
     (let [start-nanos (System/nanoTime)
           context {:start-nanos start-nanos :permit-count permit-count}]
       (mapv #(assoc context :beehive (.-beehive ^BeehivePrecipice %))
             (.getPrecipices pattern permit-count start-nanos))))))

(defn pattern [beehive-vec strategy-fn acquire-count]
  (Pattern. (mapv ->BeehivePrecipice beehive-vec)
            (reify PatternStrategy
              (nextIndices [this] (strategy-fn))
              (acquireCount [this] acquire-count))))

(defn load-balancer [beehive-vec]
  (Pattern. (mapv ->BeehivePrecipice beehive-vec)
            (RoundRobinLoadBalancer. (count beehive-vec))))

(defn shotgun [beehive-vec submission-count]
  (Pattern. (mapv ->BeehivePrecipice beehive-vec)
            (Shotgun. (count beehive-vec) submission-count)))