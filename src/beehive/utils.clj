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

(ns beehive.utils
  (:import (java.util.concurrent TimeUnit)))

(def time-units {:milliseconds TimeUnit/MILLISECONDS
                 :seconds TimeUnit/SECONDS
                 :minutes TimeUnit/MINUTES
                 :hours TimeUnit/HOURS
                 :days TimeUnit/DAYS})

(defn ->time-unit [unit-in-keyword]
  (case unit-in-keyword
    :milliseconds TimeUnit/MILLISECONDS
    :seconds TimeUnit/SECONDS
    :minutes TimeUnit/MINUTES
    :hours TimeUnit/HOURS
    :days TimeUnit/DAYS
    (throw
      (IllegalArgumentException.
        ^String
        (format "Invalid time unit argument: %s. Valid arguments are: %s."
                unit-in-keyword
                (vec (keys time-units)))))))