(ns beehive.service-test
  (:require [clojure.test :refer :all]
            [beehive.core :as beehive]
            [beehive.service :as service])
  (:import (java.io IOException)
           (java.util.concurrent CountDownLatch)))

(set! *warn-on-reflection* true)

(def service nil)

(defn- start-and-stop [f]
  (alter-var-root #'service (fn [_] (beehive/service "" 1 1)))
  (f)
  (service/shutdown service))


(use-fixtures :each start-and-stop)

(defn- block-fn [result ^CountDownLatch latch]
  (fn [] (.await latch) result))

(defn- success-fn [result]
  (fn [] result))

(defn- error-fn [e]
  (fn [] (throw e)))

(deftest submit-test
  (testing "Submit action returns CLJ future wrapping result"
    (let [latch (CountDownLatch. 1)
          f (service/submit-action service (block-fn 64 latch) Long/MAX_VALUE)]
      (is (= :pending (:status f)))
      (is (not (realized? f)))
      (is (:pending? f))
      (is (= :not-done (deref f 100 :not-done)))
      (.countDown latch)
      (is (= 64 @f))
      (is (= :success (:status f)))
      (is (:success? f))
      (is (not (:error? f)))
      (is (not (:timeout? f)))
      (is (not (:rejected? f)))
      (is (nil? (:error f)))))
  (testing "Submitted action can return error"
    (let [exception (IOException.)
          f (service/submit-action service (error-fn exception) 10000)]
      (is (= exception @f))
      (is (= exception (:error f)))
      (is (nil? (:result f)))
      (is (:error? f))
      (is (not (:success? f)))
      (is (not (:timeout? f)))
      (is (not (:rejected? f)))
      (is (= :error (:status f)))))
  (testing "Submitted action can timeout"
    (let [latch (CountDownLatch. 1)
          f (service/submit-action service (block-fn 1 latch) 50)]
      (is (= :timeout @f))
      (is (:timeout? f))
      (is (not (:success? f)))
      (is (not (:error? f)))
      (is (not (:rejected? f)))
      (is (= :timeout (:status f)))
      (.countDown latch)
      (is (nil? (:result f)))
      (is (nil? (:error f)))))
  (testing "If concurrency level exhausted, action rejected"
    (let [latch (CountDownLatch. 1)
          _ (service/submit-action service (block-fn 1 latch) Long/MAX_VALUE)
          f (service/submit-action service (success-fn 1) Long/MAX_VALUE)]
      (is (= :max-concurrency-level-exceeded @f))
      (is (:rejected? f))
      (is (not (:timeout? f)))
      (is (not (:success? f)))
      (is (not (:error? f)))
      (is (= :rejected (:status f)))
      (.countDown latch))))

(deftest metrics-test
  (testing "Testing that metrics are updated with result of action"
    (let [metrics-service (beehive/service "test" 1 100)
          latch (CountDownLatch. 1)]
      @(service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE)
      @(service/submit-action metrics-service (error-fn (IOException.)) Long/MAX_VALUE)
      @(service/submit-action metrics-service (block-fn 1 latch) 10)
      (.countDown latch)
      (is (= 1 (-> metrics-service :metrics :successes)))
      (is (= 1 (-> metrics-service :metrics :timeouts)))
      (is (= 1 (-> metrics-service :metrics :errors)))
      (is (= {"circuit-open" 0
              "errors" 1
              "max-1-circuit-open" 0
              "max-1-errors" 1
              "max-1-max-concurrency" 0
              "max-1-queue-full" 0
              "max-1-successes" 1
              "max-1-timeouts" 1
              "max-1-total" 3
              "max-2-circuit-open" 0
              "max-2-errors" 1
              "max-2-max-concurrency" 0
              "max-2-queue-full" 0
              "max-2-successes" 1
              "max-2-timeouts" 1
              "max-2-total" 3
              "max-concurrency" 0
              "queue-full" 0
              "successes" 1
              "timeouts" 1
              "total" 3}
             (-> metrics-service :metrics :snapshot)))))
  (testing "Testing that rejection reasons are updated"
    (let [metrics-service (beehive/service "test" 1 1)
          latch (CountDownLatch. 1)]
      (service/open-circuit! metrics-service)
      @(service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE)
      (is (= 1 (-> metrics-service :metrics :circuit-open)))
      (service/close-circuit! metrics-service)

      (service/submit-action metrics-service (block-fn 1 latch) Long/MAX_VALUE)
      (service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE)
      (.countDown latch)
      (is (= 1 (-> metrics-service :metrics :max-concurrency-level-exceeded)))
      (service/shutdown metrics-service))))