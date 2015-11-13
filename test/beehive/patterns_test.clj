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

(ns beehive.patterns-test
  (:use [clojure.test])
  (:require [beehive.core :as beehive]
            [beehive.service :as service]
            [beehive.patterns :as patterns])
  (:import (java.util.concurrent CountDownLatch)
           (java.util.concurrent.atomic AtomicInteger)))

(set! *warn-on-reflection* true)

(def service1 nil)
(def service2 nil)
(def service3 nil)

(defn- start-and-stop [f]
  (alter-var-root #'service1 (fn [_] (beehive/service "1" 2 1)))
  (alter-var-root #'service2 (fn [_] (beehive/service "2" 2 1)))
  (alter-var-root #'service3 (fn [_] (beehive/service "3" 2 1)))
  (f)
  (service/shutdown service1)
  (service/shutdown service2)
  (service/shutdown service3))

(use-fixtures :each start-and-stop)

(defn blocking-action [^CountDownLatch latch ^CountDownLatch submitted-latch]
  (fn [] (.countDown submitted-latch) (.await latch)))

(deftest load-balancer
  (let [load-balancer (patterns/load-balancer {service1 {:result 1}
                                               service2 {:result 2}
                                               service3 {:result 3}})]
    (testing "Submitted Actions will be spread among services."
      (is (= #{1 2 3}
             (set (for [_ (range 3)]
                    @(patterns/submit-action
                       load-balancer (fn [context] (:result context 10)) 1000)))))
      (is (= #{1 2 3}
             (set (for [_ (range 3)]
                    (patterns/run-action
                      load-balancer (fn [context] (:result context 10))))))))

    (testing "If action rejected, other services will be called."
      (let [submitted-latch (CountDownLatch. 2)
            latch (CountDownLatch. 1)
            action (blocking-action latch submitted-latch)]
        (beehive/submit-action service1 action Long/MAX_VALUE)
        (beehive/submit-action service3 action Long/MAX_VALUE)
        (.await submitted-latch)
        (is (= 2 @(patterns/submit-action load-balancer
                                          (fn [context] (:result context 10))
                                          1000)))
        (is (= 2 (patterns/run-action load-balancer
                                      (fn [context] (:result context 10)))))
        (.countDown latch)))
    (testing ":all-services-rejected returned if all services reject action"
      (let [submitted-latch (CountDownLatch. 3)
            latch (CountDownLatch. 1)
            action (blocking-action latch submitted-latch)]
        (beehive/submit-action service1 action Long/MAX_VALUE)
        (beehive/submit-action service2 action Long/MAX_VALUE)
        (beehive/submit-action service3 action Long/MAX_VALUE)
        (.await submitted-latch)
        (is (= :all-services-rejected
               @(patterns/submit-action
                  load-balancer
                  (fn [context] (:result context 10))
                  1000)))
        (is (= :all-services-rejected
               (patterns/run-action
                 load-balancer
                 (fn [context] (:result context 10)))))
        (.countDown latch)))))

(deftest shotgun
  (let [shotgun (patterns/shotgun {service1 {} service2 {} service3 {}} 2)
        all-done (atom (CountDownLatch. 2))
        action-blocking-latch (atom (CountDownLatch. 1))
        test-blocking-latch (atom (CountDownLatch. 1))
        counter (AtomicInteger. 0)
        action-fn (fn [_] (let [result (.incrementAndGet counter)]
                            (if (= 1 result)
                              (.await ^CountDownLatch @action-blocking-latch)
                              (.countDown ^CountDownLatch @test-blocking-latch))
                            (.countDown ^CountDownLatch @all-done)
                            result))]
    (testing "Actions submitted to multiple services"
      (.set counter 0)
      (reset! all-done (CountDownLatch. 2))
      (reset! action-blocking-latch (CountDownLatch. 1))
      (reset! test-blocking-latch (CountDownLatch. 1))
      (is (= 2 @(patterns/submit-action shotgun action-fn Long/MAX_VALUE)))
      (.countDown ^CountDownLatch @action-blocking-latch)
      (.await ^CountDownLatch @test-blocking-latch)
      (is (= 2 (.get counter)))
      (.await ^CountDownLatch @all-done))
    (testing "Nil returned if all services reject action"
      (.set counter 0)
      (reset! action-blocking-latch (CountDownLatch. 1))
      (reset! test-blocking-latch (CountDownLatch. 1))
      (let [shotgun (patterns/shotgun {service1 {}
                                       service2 {}
                                       service3 {}}
                                      3)
            action-fn (fn [_] (.await ^CountDownLatch @action-blocking-latch))]
        (is (not (:rejected? (patterns/submit-action shotgun
                                                     action-fn
                                                     Long/MAX_VALUE))))
        (is (= :all-services-rejected @(patterns/submit-action
                                         shotgun
                                         action-fn
                                         Long/MAX_VALUE)))
        (.countDown ^CountDownLatch @action-blocking-latch)))))