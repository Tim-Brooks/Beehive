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
  (:import (beehive.java BeehiveRejected)
           (net.uncontended.precipice.concurrent Eventual)))

(set! *warn-on-reflection* true)

(def pending [:rejected? :cancelled? :result :value])
(def success [:pending? :rejected? :cancelled? :failure?])
(def error [:pending? :rejected? :cancelled? :success?])

(defn- remove-false [f ks]
  (filter (fn [func] (func f)) ks))

(def statuses (beehive.enums/result-keys->enum {:test-success true
                                                :test-error false}))

(deftest future-test
  (testing "Test that pending futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (is (:pending? future))
      (is (= [] (remove-false future pending)))
      (.complete eventual (:test-success statuses) 4)))

  (testing "Test that success futures work correctly."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (.complete eventual (:test-success statuses) 4)
      (is (= :test-success (:result future)))
      (is (= 4 (:value future)))
      (is (= [] (remove-false future success)))))

  (testing "Test that error futures work correctly."
    (let [eventual (Eventual.)
          ex (RuntimeException.)
          future (f/->BeehiveFuture eventual)]
      (.completeExceptionally eventual (:test-error statuses) ex)
      (is (= :test-error (:result future)))
      (is (= ex (:value future)))
      (is (= [] (remove-false future error))))))

(deftest rejected-future-test
  (testing "Test that pending futures work correctly."
    (let [future (f/rejected-future :circuit-open)]
      (is (not (:pending? future)))
      (is (not (:cancelled? future)))
      (is (:rejected? future))
      (is (= :circuit-open (:rejected-reason future)))
      (try
        @future
        (catch BeehiveRejected e
          (is (= :circuit-open (:rejected-reason e))))))))

(deftest callback-test
  (testing "Test callback on success future."
    (let [eventual (Eventual.)
          future (f/->BeehiveFuture eventual)]
      (f/on-complete
        future
        (fn [map]
          (is (= {:failure? false
                  :result :test-success
                  :success? true
                  :value 4}
                 map))))
      (.complete eventual (:test-success statuses) 4)))

  (testing "Test callback on error future."
    (let [eventual (Eventual.)
          ex (RuntimeException.)
          future (f/->BeehiveFuture eventual)]
      (f/on-complete
        future
        (fn [map]
          (is (= {:failure? true
                  :result :test-error
                  :success? false
                  :value ex}
                 map))))
      (.completeExceptionally eventual (:test-error statuses) ex)))

  (testing "Test callback on rejected future."
    (let [future (f/rejected-future :max-concurrency)]
      (f/on-complete
        future
        (fn [map]
          (is (= {:rejected-reason :max-concurrency
                  :rejected? true}
                 map)))))))
