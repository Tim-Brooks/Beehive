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

(ns beehive.compatibility
  (:import (net.uncontended.precipice.core ResilientAction)
           (net.uncontended.precipice.core.pattern ResilientPatternAction)))

(defn wrap-pattern-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context] (action-fn context))))

(defn wrap-action-fn [action-fn]
  (reify ResilientAction
    (run [_] (action-fn))))
