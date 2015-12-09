;; Copyright 2015 Timothy Brooks
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

(ns beehive.future-test
  (:require [clojure.test :refer :all]
            [beehive.future :as f])
  (:import (net.uncontended.precipice.concurrent Eventual)))

(defn remove-false [f & ks]
  (filter (fn [func] (func f)) ks))

(deftest future-status
  (testing "Test that statuses work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (is (= :pending (:status future)))
      (is (:pending? future))
      (.complete eventual 4)
      (is (= :success (:status future)))
      (is (:success? future))
      (is (= [] (remove-false future :pending?))))))
