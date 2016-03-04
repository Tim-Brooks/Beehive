(ns beehive.hive-test
  (:require [clojure.test :refer :all]
            [beehive.hive :as hive]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore])
  (:import (beehive.enums ToCLJ)
           (net.uncontended.precipice Failable)))

(deftest guard-rail-test
  (testing "Types are properly generated."
    (let [hive (hive/beehive
                 "Test"
                 (hive/results
                   {:test-success true :test-error false}
                   (metrics/rolling-count-metrics))
                 (hive/create-back-pressure
                   #{:max-concurrency}
                   (metrics/rolling-count-metrics)
                   (semaphore/semaphore 4 :max-concurrency)))
          {:keys [test-success test-error]} (:result-key->enum hive)
          {:keys [max-concurrency]} (:rejected-key->enum hive)]
      (is (= "test$DASH$success" (str test-success)))
      (is (not (.isFailure ^Failable test-success)))
      (is (.isSuccess ^Failable test-success))
      (is (= :test-success (.keyword ^ToCLJ test-success)))
      (is (= "test$DASH$error$FAILURE$" (str test-error)))
      (is (not (.isSuccess ^Failable test-error)))
      (is (.isFailure ^Failable test-error))
      (is (= :test-error (.keyword ^ToCLJ test-error)))
      (is (= "max$DASH$concurrency" (str max-concurrency)))
      (is (= :max-concurrency (.keyword ^ToCLJ max-concurrency))))))