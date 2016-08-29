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
  (:import (net.uncontended.precipice.metrics.counts TotalCounts RollingCounts CountRecorder)
           (net.uncontended.precipice.metrics.latency LatencyRecorder)))

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
      (is (= 1 (:count (first (metrics/count-seq metrics :test-success)))))
      (is (= 1 (:count (first (metrics/count-seq metrics :test-error)))))
      (is (= 1 (:count (first (metrics/count-seq metrics :test-timeout)))))
      (is (= {:test-error 1
              :test-success 1
              :test-timeout 1} (:counts (first (metrics/counts-seq metrics)))))))

  (testing "Testing count recorder returns the results of the underlying java class"
    (let [metrics (metrics/count-recorder clazz)
          ^CountRecorder java-metrics (:precipice-metrics metrics)]
      (.write java-metrics (:test-success key->enum) 1 (System/nanoTime))
      (.write java-metrics (:test-error key->enum) 1 (System/nanoTime))
      (.write java-metrics (:test-timeout key->enum) 1 (System/nanoTime))
      (let [{:keys [counts start-millis end-millis]} (first (metrics/counts-seq metrics))]
        (is (= {:test-error 1 :test-success 1 :test-timeout 1} counts))
        (is (>= end-millis start-millis)))
      (doseq [k [:test-success :test-error :test-timeout]]
        (let [{:keys [count start-millis end-millis]} (first (metrics/count-seq metrics k))]
          (is (= 1 count))
          (is (>= end-millis start-millis))))
      (let [{:keys [counts start-millis end-millis]} (first (metrics/counts-seq metrics))]
        (is (= {:test-error 1 :test-success 1 :test-timeout 1} counts))
        (is (>= end-millis start-millis)))
      (let [{:keys [counts start-millis end-millis] :as swapped} (metrics/counter-swap! metrics)]
        (is (= {:test-error 1 :test-success 1 :test-timeout 1} counts))
        (is (>= end-millis start-millis))
        (doseq [k [:test-success :test-error :test-timeout]]
          (let [{:keys [count start-millis end-millis]} (first (metrics/count-seq metrics k))]
            (is (= 0 count))
            (is (>= end-millis start-millis))))
        (let [{:keys [counts start-millis end-millis]} (first (metrics/counts-seq metrics))]
          (is (= {:test-error 0 :test-success 0 :test-timeout 0} counts))
          (is (>= end-millis start-millis)))
        (metrics/counter-swap! metrics swapped)
        (is (= {:test-error 0 :test-success 0 :test-timeout 0} (:counts (metrics/counter-swap! metrics)))))))

  (testing "Testing latency recorder returns the results of the underlying java class"
    (let [metrics (metrics/latency-recorder clazz)
          ^LatencyRecorder java-metrics (:precipice-metrics metrics)]
      (.write java-metrics (:test-success key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-error key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-timeout key->enum) 1 1 (System/nanoTime))
      (let [{:keys [latencies start-millis end-millis]} (first (metrics/latencies-seq metrics))]
        (is (= {:test-error {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-success {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-timeout {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}} latencies))
        (is (>= end-millis start-millis)))
      (doseq [k [:test-success :test-error :test-timeout]]
        (let [{:keys [latency start-millis end-millis]} (first (metrics/latency-seq metrics k))]
          (is (= {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1} latency))
          (is (>= end-millis start-millis))))
      (let [{:keys [latencies start-millis end-millis] :as swapped} (metrics/latency-swap! metrics)]
        (is (= {:test-error {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-success {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-timeout {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}} latencies))
        (is (>= end-millis start-millis))
        (doseq [k [:test-success :test-error :test-timeout]]
          (let [{:keys [latency start-millis end-millis]} (first (metrics/latency-seq metrics k))]
            (is (= {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0} latency))
            (is (>= end-millis start-millis))))
        (let [{:keys [latencies start-millis end-millis]} (first (metrics/latencies-seq metrics))]
          (is (= {:test-error {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                  :test-success {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                  :test-timeout {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}} latencies))
          (is (>= end-millis start-millis)))
        (metrics/latency-swap! metrics swapped)
        (is (= {:test-error {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                :test-success {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                :test-timeout {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}}
               (:latencies (metrics/latency-swap! metrics)))))))

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