(ns beehive.hive-test
  (:require [clojure.test :refer :all]
            [beehive.hive :as hive]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore])
  (:import (beehive.java ToCLJ)
           (net.uncontended.precipice Failable)))

(def beehive nil)

(defn- create-hive [f]
  (let [hive (hive/beehive
               "Test"
               (hive/results
                 {:test-success true :test-error false}
                 (metrics/rolling-count-metrics))
               (hive/create-back-pressure
                 #{:max-concurrency}
                 (metrics/rolling-count-metrics)
                 (semaphore/semaphore 5 :max-concurrency)))]
    (alter-var-root
      #'beehive
      (fn [_] hive)))
  (f))

(use-fixtures :each create-hive)

(deftest type-test
  (testing "Types are properly generated."
    (let [{:keys [test-success test-error]} (:result-key->enum beehive)
          {:keys [max-concurrency]} (:rejected-key->enum beehive)]
      (is (= "test$DASH$success" (str test-success)))
      (is (not (.isFailure ^Failable test-success)))
      (is (.isSuccess ^Failable test-success))
      (is (= :test-success (.keyword ^ToCLJ test-success)))
      (is (= "test$DASH$error$FAILURE$" (str test-error)))
      (is (not (.isSuccess ^Failable test-error)))
      (is (.isFailure ^Failable test-error))
      (is (= :test-error (.keyword ^ToCLJ test-error)))
      (is (= "max$DASH$concurrency" (str max-concurrency)))
      (is (= :max-concurrency (.keyword ^ToCLJ max-concurrency)))))

  (testing "Cannot pass arbitrary types to complete completable"
    (try
      (hive/complete! (hive/completable beehive 1) :wrong-type "result")
      (catch IllegalArgumentException e
        (is (= "Invalid result ':wrong-type'; Valid results are '(:test-success :test-error)'"
               (.getMessage e))))))

  (testing "Cannot pass arbitrary types to complete promise"
    (try
      (hive/complete! (hive/promise beehive 1) :wrong-type "result")
      (catch IllegalArgumentException e
        (is (= "Invalid result ':wrong-type'; Valid results are '(:test-success :test-error)'"
               (.getMessage e)))))))

(deftest acquire-and-release-test
  (testing "Rejections and releases work as expected."
    (let [semaphore (first (hive/back-pressure beehive))]
      (is (= {:permit-count 1
              :start-nanos 100} (hive/acquire beehive 1 100)))
      (is (= {:permit-count 4
              :start-nanos 150} (hive/acquire beehive 4 150)))
      (is (= 5 (semaphore/concurrency-level semaphore)))
      (is (= {:rejected-reason :max-concurrency
              :rejected? true} (hive/acquire beehive 1 200)))
      (hive/release-without-result beehive {:permit-count 1 :start-nanos 100})
      (is (= 4 (semaphore/concurrency-level semaphore)))
      (is (= {:permit-count 1
              :start-nanos 500} (hive/acquire beehive 1 500)))
      (hive/release-raw-permits beehive 5)))

  (testing "Completables are wired up to release permits on completion."
    (let [semaphore (first (hive/back-pressure beehive))
          completable (hive/completable beehive 5)]
      (is (false? (:rejected? completable)))
      (is (= {:rejected-reason :max-concurrency
              :rejected? true} (hive/completable beehive 1)))
      (hive/complete! completable :test-success "Hello")
      (is (= 0 (semaphore/concurrency-level semaphore)))
      (is (false? (:rejected? (hive/completable beehive 1))))
      (hive/release-raw-permits beehive 1)
      (is (= 0 (semaphore/concurrency-level semaphore)))))

  (testing "Promises are wired up to release permits on completion."
    (let [semaphore (first (hive/back-pressure beehive))
          promise (hive/acquire-promise beehive 5)]
      (is (false? (:rejected? promise)))
      (is (= {:rejected-reason :max-concurrency
              :rejected? true} (hive/promise beehive 1)))
      (hive/complete! promise :test-success "Hello")
      (is (= 0 (semaphore/concurrency-level semaphore)))
      (is (false? (:rejected? (hive/promise beehive 1))))
      (hive/release-raw-permits beehive 1)
      (is (= 0 (semaphore/concurrency-level semaphore))))))

(deftest metrics-test
  (let [result-metrics (hive/result-metrics beehive)]
    (testing "Metrics are updated on release with result."
      (is (= 0 (metrics/total-count result-metrics :test-error)))
      (hive/release beehive (hive/acquire beehive 1) :test-error)
      (is (= 1 (metrics/total-count result-metrics :test-error))))
    (testing "Metrics are updated on completable complete."
      (is (= 0 (metrics/total-count result-metrics :test-success)))
      (hive/complete! (hive/completable beehive 1) :test-success "result")
      (is (= 1 (metrics/total-count result-metrics :test-success))))
    (testing "Metrics are updated on promise complete."
      (is (= 1 (metrics/total-count result-metrics :test-success)))
      (hive/complete! (hive/promise beehive 1) :test-success "result")
      (is (= 2 (metrics/total-count result-metrics :test-success))))))

(deftest context-tests
  (testing "Completable can be converted into result view"
    (let [completable (hive/completable beehive 1)]
      (is (= {:pending? true
              :rejected? false} (hive/to-result-view completable)))
      (hive/complete! completable :test-success 5)
      (is (= {:failure? false
              :result :test-success
              :success? true
              :value 5} (hive/to-result-view completable))))
    (let [ex (RuntimeException.)
          completable (hive/completable beehive 1)]
      (hive/complete! completable :test-error ex)
      (is (= {:failure? true
              :result :test-error
              :success? false
              :value ex} (hive/to-result-view completable)))))
  (testing "Promise can be converted into future"
    (let [promise (hive/promise beehive 1)
          future (hive/to-future promise)]
      (is (:pending? future))
      (hive/complete! promise :test-success 5)
      (is (= :test-success (:result future)))
      (is (= 5 (:value future))))
    (let [ex (RuntimeException.)
          promise (hive/promise beehive 1)
          future (hive/to-future promise)]
      (hive/complete! promise :test-error ex)
      (is (= :test-error (:result future)))
      (is (= ex (:value future))))))