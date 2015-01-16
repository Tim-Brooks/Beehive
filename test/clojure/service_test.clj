(ns clojure.service-test
  (:use [clojure.test])
  (:require [fault.core :as fault]
            [fault.service :as service])
  (:import (java.io IOException)
           (java.util.concurrent CountDownLatch)
           (fault.metrics ActionMetrics)
           (fault.service CLJMetrics CLJService)))

(set! *warn-on-reflection* true)

(def service nil)

(defn- start-and-stop [f]
  (alter-var-root #'service (fn [_] (fault/service 1 1)))
  (f)
  (service/shutdown service))


(use-fixtures :each start-and-stop)

(deftest submit-test
  (testing "Submit action returns CLJ future wrapping result"
    (let [latch (CountDownLatch. 1)
          f (service/submit-action service
                                   (fn [] (.await latch) (* 8 8))
                                   Long/MAX_VALUE)]
      (is (= :pending (:status f)))
      (is (not (realized? f)))
      (is (= :not-done (deref f 100 :not-done)))
      (.countDown latch)
      (is (= 64 @f))
      (is (= :success (:status f)))
      (is (nil? (:error f)))))
  (testing "Submitted action can return error"
    (let [exception (IOException.)
          f (service/submit-action service (fn [] (throw exception)) 10000)]
      (is (= exception @f))
      (is (= exception (:error f)))
      (is (nil? (:result f)))
      (is (= :error (:status f)))))
  (testing "Submitted action can timeout"
    (let [latch (CountDownLatch. 1)
          f (service/submit-action service (fn [] (.await latch)) 50)]
      (is (= :timed-out @f))
      (is (= :timed-out (:status f)))
      (.countDown latch)
      (is (nil? (:result f)))
      (is (nil? (:error f)))))
  (testing "If concurrency level exhausted, action rejected"
    (let [latch (CountDownLatch. 1)
          _ (service/submit-action service (fn [] (.await latch)) Long/MAX_VALUE)
          f (service/submit-action service (fn [] 1) Long/MAX_VALUE)]
      (is (= :max-concurrency-level-exceeded @f))
      (is (= :rejected (:status f)))
      (.countDown latch))))

(deftest metrics-test
  (testing "Testing that metrics are updated"
    (let [metrics-service (fault/service 1 100)
          _ @(service/submit-action metrics-service (fn [] 1) Long/MAX_VALUE)]
      (is (= 1 (-> metrics-service :metrics :successes))))))