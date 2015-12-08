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

(ns beehive.service-test
  (:require [clojure.test :refer :all]
            [beehive.core :as beehive]
            [beehive.service :as service]
            [beehive.future :as f])
  (:import (java.io IOException)
           (java.util.concurrent CountDownLatch ExecutionException)))

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
      (is (= 1 (service/pending-count service)))
      (is (= 0 (service/remaining-capacity service)))
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
      (is (nil? (:error f)))
      (is (= 0 (service/pending-count service)))
      (is (= 1 (service/remaining-capacity service)))))
  (testing "Submitted action can return error"
    (let [exception (IOException.)
          f (service/submit-action service (error-fn exception) 10000)]
      (f/await f)
      (is (= exception (:error f)))
      (is (nil? (:result f)))
      (is (:error? f))
      (is (not (:success? f)))
      (is (not (:timeout? f)))
      (is (not (:rejected? f)))
      (is (= :error (:status f)))
      (try
        @f
        (is false)
        (catch ExecutionException e
          (is (= exception (.getCause e)))))))
  (testing "Submitted action can timeout"
    (let [latch (CountDownLatch. 1)
          f (service/submit-action service (block-fn 1 latch) 50)]
      ;; TODO: Should change when precipice changes.
      (is (nil? @f))
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

(deftest callback-test
  (testing "Test that future callback is executed"
    (let [status (atom nil)
          result (atom nil)
          blocker (CountDownLatch. 1)
          f (service/submit-action service (success-fn 64) Long/MAX_VALUE)]
      (f/on-complete
        f (fn [s r] (reset! status s) (reset! result r) (.countDown blocker)))
      (.await blocker)
      (is (= :success @status))
      (is (= 64 @result)))
    (let [status (atom nil)
          result (atom nil)
          blocker (CountDownLatch. 1)
          e (RuntimeException.)
          f (service/submit-action service (error-fn e) Long/MAX_VALUE)]
      (f/on-complete
        f (fn [s r] (reset! status s) (reset! result r) (.countDown blocker)))
      (.await blocker)
      (is (= :error @status))
      (is (= e @result)))
    (let [action-blocker (CountDownLatch. 1)
          status (atom nil)
          result (atom nil)
          blocker (CountDownLatch. 1)
          f (service/submit-action service (block-fn 64 action-blocker) 10)]
      (f/on-complete
        f (fn [s r] (reset! status s) (reset! result r) (.countDown blocker)))
      (.await blocker)
      (is (= :timeout @status))
      (is (nil? @result))
      (.countDown action-blocker))))

(deftest metrics-test
  (testing "Testing that metrics are updated with result of action"
    (let [metrics-service (beehive/service "test" 1 100)
          latch (CountDownLatch. 1)]
      (f/await (service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE))
      (f/await (service/submit-action metrics-service (error-fn (IOException.)) Long/MAX_VALUE))
      (f/await (service/submit-action metrics-service (block-fn 1 latch) 10))
      (.countDown latch)
      (is (= 1 (-> metrics-service service/metrics :successes)))
      (is (= 1 (-> metrics-service service/metrics :timeouts)))
      (is (= 1 (-> metrics-service service/metrics :errors)))
      (is (= {:all-rejected 0
              :circuit-open 0
              :errors 1
              :max-1-all-rejected 0
              :max-1-circuit-open 0
              :max-1-errors 1
              :max-1-max-concurrency 0
              :max-1-queue-full 0
              :max-1-successes 1
              :max-1-timeouts 1
              :max-1-total 3
              :max-2-all-rejected 0
              :max-2-circuit-open 0
              :max-2-errors 1
              :max-2-max-concurrency 0
              :max-2-queue-full 0
              :max-2-successes 1
              :max-2-timeouts 1
              :max-2-total 3
              :max-concurrency 0
              :queue-full 0
              :successes 1
              :timeouts 1
              :total 3
              :total-all-rejected 0
              :total-circuit-open 0
              :total-errors 1
              :total-max-concurrency 0
              :total-queue-full 0
              :total-successes 1
              :total-timeouts 1
              :total-total 3}
             (service/metrics metrics-service)))))
  (testing "Testing that rejection reasons are updated"
    (let [metrics-service (beehive/service "test" 1 1)
          latch (CountDownLatch. 1)]
      (service/open-circuit! metrics-service)
      @(service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE)
      (is (= 1 (-> metrics-service service/metrics :circuit-open)))
      (service/close-circuit! metrics-service)

      (service/submit-action metrics-service (block-fn 1 latch) Long/MAX_VALUE)
      (service/submit-action metrics-service (success-fn 1) Long/MAX_VALUE)
      (.countDown latch)
      (is (= 1 (-> metrics-service service/metrics :max-concurrency)))
      (service/shutdown metrics-service))))

(deftest latency-test
  (testing "Testing that latency is updated"
    (let [latency-service (beehive/service "test" 1 100)
          latch (CountDownLatch. 1)]
      (f/await (service/submit-action latency-service (success-fn 1) Long/MAX_VALUE))
      (f/await (service/submit-action latency-service (error-fn (IOException.)) Long/MAX_VALUE))
      (f/await (service/submit-action latency-service (block-fn 1 latch) 10))
      (.countDown latch)
      (let [{:keys [latency-50
                    latency-90
                    latency-99
                    latency-99-9
                    latency-99-99
                    latency-99-999
                    latency-max
                    latency-mean]} (service/latency latency-service)]
        (is (not (or (nil? latency-50) (= latency-50 0))))
        (is (not (or (nil? latency-90) (= latency-90 0))))
        (is (not (or (nil? latency-99) (= latency-99 0))))
        (is (not (or (nil? latency-99-9) (= latency-99-9 0))))
        (is (not (or (nil? latency-99-99) (= latency-99-99 0))))
        (is (not (or (nil? latency-99-999) (= latency-99-999 0))))
        (is (not (or (nil? latency-max) (= latency-max 0))))
        (is (not (or (nil? latency-mean) (= latency-mean 0.0))))))))

(deftest circuit-breaker-config-test
  (testing "Testing that the service is created with the correct circuit breaker config"
    (let [svc (beehive/service "test"
                               1
                               100
                               :breaker {:trailing-period-millis 999
                                         :failure-threshold Long/MAX_VALUE})]
      (is (= 999
             (:time-period-in-millis (:config (:breaker svc)))))
      (is (= Long/MAX_VALUE
             (:failure-threshold (:config (:breaker svc)))))
      ;; Defaults if the key is not passed in the config
      (is (= 1000
             (:time-to-pause-millis (:config (:breaker svc))))))))
