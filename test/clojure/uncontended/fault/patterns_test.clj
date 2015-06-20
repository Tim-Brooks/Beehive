(ns uncontended.fault.patterns-test
  (:use [clojure.test])
  (:require [uncontended.fault.core :as fault]
            [uncontended.fault.service :as service]
            [uncontended.fault.patterns :as patterns])
  (:import (java.util.concurrent CountDownLatch)))

(set! *warn-on-reflection* true)

(def service1 nil)
(def service2 nil)
(def service3 nil)

(defn- start-and-stop [f]
  (alter-var-root #'service1 (fn [_] (fault/service "1" 1 1)))
  (alter-var-root #'service2 (fn [_] (fault/service "2" 1 1)))
  (alter-var-root #'service3 (fn [_] (fault/service "3" 1 1)))
  (f)
  (service/shutdown service1)
  (service/shutdown service2)
  (service/shutdown service3))

(use-fixtures :each start-and-stop)

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
                    @(patterns/perform-action
                       load-balancer (fn [context] (:result context 10))))))))
    (testing "If action rejected, other services will be called."
      (let [latch (CountDownLatch. 1)]
        (fault/submit-action service1 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service3 (fn [] (.await latch)) Long/MAX_VALUE)
        (is (= 2 @(patterns/submit-action load-balancer
                                          (fn [context] (:result context 10))
                                          1000)))
        (is (= 2 @(patterns/perform-action load-balancer
                                           (fn [context] (:result context 10)))))
        (.countDown latch)))
    (testing ":all-services-rejected returned if all services reject action"
      (let [latch (CountDownLatch. 1)]
        (fault/submit-action service1 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service2 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service3 (fn [] (.await latch)) Long/MAX_VALUE)
        (is (= :all-services-rejected
               @(patterns/submit-action
                  load-balancer
                  (fn [context] (:result context 10))
                  1000)))
        (is (= :all-services-rejected
               @(patterns/perform-action
                  load-balancer
                  (fn [context] (:result context 10)))))
        (.countDown latch)))))

(deftest shotgun
  (let [shotgun (patterns/shotgun {service1 {}
                                   service2 {}
                                   service3 {}}
                                  2)
        action-blocking-latch (atom (CountDownLatch. 1))
        test-blocking-latch (atom (CountDownLatch. 1))
        counter (atom 0)
        inc-fn (fn [current]
                 (if (= 1 current)
                   (do (.await ^CountDownLatch @action-blocking-latch)
                       (inc current))
                   (inc current)))
        action-fn (fn [] (let [result (swap! counter inc-fn)]
                           (when (= result 2)
                             (.countDown ^CountDownLatch @test-blocking-latch))
                           result))]
    (testing "Actions submitted to multiple services"
      (reset! counter 0)
      (reset! action-blocking-latch (CountDownLatch. 1))
      (reset! test-blocking-latch (CountDownLatch. 1))
      (is (= 1
             @(patterns/submit-action shotgun
                                      (fn [_] (action-fn))
                                      Long/MAX_VALUE)))
      (.countDown ^CountDownLatch @action-blocking-latch)
      (.await ^CountDownLatch @test-blocking-latch)
      (is (= 2 @counter)))
    (testing "Result is from the the first services to response"
      (reset! counter 0)
      (reset! action-blocking-latch (CountDownLatch. 1))
      (reset! test-blocking-latch (CountDownLatch. 1))
      (let [f (patterns/submit-action shotgun
                                      (fn [_] (action-fn))
                                      Long/MAX_VALUE)]
        @f
        (.countDown ^CountDownLatch @action-blocking-latch)
        (.await ^CountDownLatch @test-blocking-latch)
        (is (= 1 @f))))
    (testing "Nil returned if all services reject action"
      (reset! counter 0)
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