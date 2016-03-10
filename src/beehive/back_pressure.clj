;; Copyright 2016 Timothy Brooks
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

(ns beehive.back-pressure
  (:import (net.uncontended.precipice BackPressure)))

(defn mechanism
  "This is experimental."
  [fn & {:keys [pass-nano-time?] :or {pass-nano-time? false}}]
  (if pass-nano-time?
    (with-meta
      (reify BackPressure
        (acquirePermit [this permit-count nano-time]
          (fn (:beehive (meta this)) permit-count nano-time))
        (releasePermit [this permit-count nano-time]
          )
        (releasePermit [this permit-count result nano-time]
          )
        (registerGuardRail [this guard-rail]))
      {:clj? true})
    (with-meta
      (reify BackPressure
        (acquirePermit [this permit-count nano-time]
          (fn (:beehive (meta this)) permit-count))
        (releasePermit [this permit-count nano-time]
          )
        (releasePermit [this permit-count result nano-time]
          )
        (registerGuardRail [this guard-rail]))
      {:clj? true})))