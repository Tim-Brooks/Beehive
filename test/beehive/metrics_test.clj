;; Copyright 2016 Timothy Brooks
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

(ns beehive.metrics-test
  (:require [clojure.test :refer :all]
            [beehive.metrics :as metrics])
  (:import (net.uncontended.precipice.metrics.counts TotalCounts RollingCounts)))

(def clazz (beehive.enums/generate-result-class
             {:test-success true
              :test-error false
              :test-timeout false}))

(def key->enum (beehive.enums/enum-class-to-keyword->enum clazz))

(deftest counts-test
  (testing "Testing total counts return the results of the underlying java class"
    (let [metrics (metrics/total-counts clazz)
          ^TotalCounts java-metrics (:precipice-metrics metrics)]
      (.add java-metrics (:test-success key->enum) 1)
      (.add java-metrics (:test-error key->enum) 1)
      (.add java-metrics (:test-timeout key->enum) 1)
      (is (= 1 (:count (first (metrics/get-count metrics :test-success)))))
      (is (= 1 (:count (first (metrics/get-count metrics :test-error)))))
      (is (= 1 (:count (first (metrics/get-count metrics :test-timeout)))))
      (is (= {:test-error 1
              :test-success 1
              :test-timeout 1} (:counts (first (metrics/get-counts metrics)))))))

  (testing "Testing rolling counts return the results of the underlying java class"
    (let [metrics (with-redefs [metrics/current-millis (fn [] 0)]
                    (metrics/rolling-counts clazz 4 1 :seconds))
          ^RollingCounts java-metrics (:precipice-metrics metrics)
          start-millis (:start-millis metrics)]
      (.write java-metrics (:test-success key->enum) 1 (System/nanoTime))
      (.write java-metrics (:test-error key->enum) 1 (System/nanoTime))
      (.write java-metrics (:test-timeout key->enum) 1 (System/nanoTime))
      (with-redefs [metrics/current-millis (fn [] 3000)]
        ; (is (= 1 (vec (drop-last (metrics/get-count metrics :test-success)))))
        )
      ; (is (= 1 (metrics/get-count metrics :test-error)))
      ; (is (= 1 (metrics/get-count metrics :test-timeout)))
      ;(is (= {:test-error 1
      ;        :test-success 1
      ;        :test-timeout 1} (:counts (first (metrics/get-counts metrics)))))
      ;
      )))