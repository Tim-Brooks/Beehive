(ns beehive.hive-test
  (:require [clojure.test :refer :all]
            [beehive.hive :as hive]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore])
  (:import (beehive.enums ToCLJ)
           (beehive.hive Hive)
           (net.uncontended.precipice Failable)))

(deftest guard-rail-test
  (testing "Types are properly generated."
    (let [^Hive hive (hive/beehive
                       "Test"
                       (metrics/count-metrics)
                       (metrics/count-metrics)
                       :back-pressure {:max-concurrency (semaphore/semaphore 4)}
                       :result->success? {:test-success true :test-error false})
          {:keys [test-success test-error]} (.-result_enums hive)
          {:keys [max-concurrency]} (.rejected_enums hive)]
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