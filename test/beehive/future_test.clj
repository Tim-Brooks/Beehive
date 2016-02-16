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
  (:import (net.uncontended.precipice.concurrent Eventual)
           (net.uncontended.precipice TimeoutableResult)))

(set! *warn-on-reflection* true)

(def pending [:success? :error? :timeout? :rejected? :cancelled? :result :error])
(def success [:pending? :error? :timeout? :rejected? :cancelled? :error])
(def error [:pending? :success? :timeout? :rejected? :cancelled? :result])
(def timeout [:pending? :success? :error? :rejected? :cancelled? :result :error])

(defn- remove-false [f ks]
  (filter (fn [func] (func f)) ks))

(deftest future-test
  (testing "Test that pending futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (is (= :pending (:status future)))
      (is (:pending? future))
      (is (= [] (remove-false future pending)))
      (.complete eventual TimeoutableResult/SUCCESS 4)))

  (testing "Test that success futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (.complete eventual TimeoutableResult/SUCCESS 4)
      (is (= :success (:status future)))
      (is (= 4 (:result future)))
      (is (:success? future))
      (is (= [] (remove-false future success)))))

  (testing "Test that error futures work correctly."
    (let [eventual (Eventual.)
          ex (RuntimeException.)
          future (f/->BeehiveFuture eventual)]
      (.completeExceptionally eventual TimeoutableResult/ERROR ex)
      (is (= :error (:status future)))
      (is (= ex (:error future)))
      (is (:error? future))
      (is (= [] (remove-false future error)))))

  (testing "Test that timeout futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (.completeExceptionally eventual TimeoutableResult/TIMEOUT nil)
      (is (= :timeout (:status future)))
      (is (:timeout? future))
      (is (= [] (remove-false future timeout))))))
