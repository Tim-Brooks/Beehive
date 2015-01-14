(ns clojure.patterns-test
  (:use [clojure.test])
  (:require [fault.core :as fault]
            [fault.patterns :as patterns])
  (:import (fault ServiceExecutor)
           (java.util.concurrent CountDownLatch)))

(set! *warn-on-reflection* true)

(def service1 nil)
(def service2 nil)
(def service3 nil)

(defn- start-and-stop [f]
  (alter-var-root #'service1 (fn [_] (fault/service 1 1)))
  (alter-var-root #'service2 (fn [_] (fault/service 1 1)))
  (alter-var-root #'service3 (fn [_] (fault/service 1 1)))
  (f)
  (.shutdown ^ServiceExecutor (:service service1))
  (.shutdown ^ServiceExecutor (:service service2))
  (.shutdown ^ServiceExecutor (:service service3)))

(use-fixtures :each start-and-stop)

(deftest load-balancer
  (let [load-balancer (patterns/load-balancer {:service1 service1
                                               :service2 service2
                                               :service3 service3})]
    (testing "Submitted Actions will be spread among services."
      (is (= #{1 2 3}
             (set (for [_ (range 3)]
                    @(patterns/submit-load-balanced-action
                       load-balancer {:service1 (fn [] 1)
                                      :service2 (fn [] 2)
                                      :service3 (fn [] 3)}
                       1000)))))
      (is (= #{1 2 3}
             (set (for [_ (range 3)]
                    @(patterns/perform-load-balanced-action
                       load-balancer {:service1 (fn [] 1)
                                      :service2 (fn [] 2)
                                      :service3 (fn [] 3)}))))))
    (testing "If action rejected, other services will be called."
      (let [latch (CountDownLatch. 1)]
        (fault/submit-action service1 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service3 (fn [] (.await latch)) Long/MAX_VALUE)
        (is (= 2 @(patterns/submit-load-balanced-action load-balancer
                                                        {:service1 (fn [] 1)
                                                         :service2 (fn [] 2)
                                                         :service3 (fn [] 3)}
                                                        1000)))
        (is (= 2 @(patterns/submit-load-balanced-action load-balancer
                                                        {:service1 (fn [] 1)
                                                         :service2 (fn [] 2)
                                                         :service3 (fn [] 3)}
                                                        1000)))
        (.countDown latch)))
    (testing "Nil returned if all services reject action"
      (let [latch (CountDownLatch. 1)]
        (fault/submit-action service1 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service2 (fn [] (.await latch)) Long/MAX_VALUE)
        (fault/submit-action service3 (fn [] (.await latch)) Long/MAX_VALUE)
        (is (= nil (patterns/submit-load-balanced-action load-balancer
                                                         {:service1 (fn [] 1)
                                                          :service2 (fn [] 2)
                                                          :service3 (fn [] 3)}
                                                         1000)))

        (.countDown latch)))))
