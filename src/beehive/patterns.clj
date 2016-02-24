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
  (:import (net.uncontended.precipice.pattern Pattern RoundRobinLoadBalancer)))

(set! *warn-on-reflection* true)

(defn load-balancer [precipice-list]
  (Pattern. precipice-list (RoundRobinLoadBalancer. (count precipice-list))))

(defn pattern-seq [pattern permit-number]
  (let [^Pattern pattern pattern]
    (.getPrecipices pattern permit-number (System/nanoTime))))