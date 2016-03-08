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

(set! *warn-on-reflection* true)

(def pending [:rejected? :cancelled? :result :error])
(def success [:pending? :rejected? :cancelled? :error])
(def error [:pending? :rejected? :cancelled? :result])

(defn- remove-false [f ks]
  (filter (fn [func] (func f)) ks))

(def statuses (beehive.enums/result-keys->enum {:test-success true
                                                :test-error false}))

(deftest future-test
  (testing "Test that pending futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (is (= :pending (:status future)))
      (is (:pending? future))
      (is (= [] (remove-false future pending)))
      (.complete eventual (:test-success statuses) 4)))

  (testing "Test that success futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (.complete eventual (:test-success statuses) 4)
      (is (= :test-success (:status future)))
      (is (= 4 (:result future)))
      (is (= [] (remove-false future success)))))

  (testing "Test that error futures work correctly."
    (let [eventual (Eventual.)
          ex (RuntimeException.)
          future (f/->BeehiveFuture eventual)]
      (.completeExceptionally eventual (:test-error statuses) ex)
      (is (= :test-error (:status future)))
      (is (= ex (:error future)))
      (is (= [] (remove-false future error))))))
