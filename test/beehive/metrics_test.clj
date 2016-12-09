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
  (:import (net.uncontended.precipice.metrics.counts RollingCounts)
           (net.uncontended.precipice.metrics.latency LatencyRecorder TotalLatency)))

(def clazz (beehive.enums/generate-result-class
             {:test-success true
              :test-error false
              :test-timeout false}))

(def result-keys [:test-success :test-error :test-timeout])

(def key->enum (beehive.enums/enum-class-to-keyword->enum clazz))

(defn- assert-single-count-seq [metrics expected]
  (doseq [k result-keys]
    (let [{:keys [count start-millis end-millis]} (first (metrics/count-seq metrics k))]
      (is (= expected count))
      (is (>= end-millis start-millis)))))

(defn- assert-single-counts-seq [metrics expected]
  (let [{:keys [counts start-millis end-millis]} (first (metrics/counts-seq metrics))]
    (is (= (into {} (mapv (fn [k] [k expected]) result-keys)) counts))
    (is (>= end-millis start-millis))))

(defn- assert-single-latency-seq [metrics expected]
  (doseq [k result-keys]
    (let [{:keys [latency start-millis end-millis]} (first (metrics/latency-seq metrics k))]
      (is (= expected latency))
      (is (>= end-millis start-millis)))))

(defn- assert-single-latencies-seq [metrics expected]
  (let [{:keys [latencies start-millis end-millis]} (first (metrics/latencies-seq metrics))]
    (is (= (into {} (mapv (fn [k] [k expected]) result-keys)) latencies))
    (is (>= end-millis start-millis))))

(deftest counts-test
  (testing "Testing total counts return the results of the underlying java class"
    (let [metrics (metrics/total-counts clazz)]
      (metrics/increment metrics :test-success)
      (metrics/increment metrics :test-error)
      (metrics/increment metrics :test-timeout)
      (assert-single-count-seq metrics 1)
      (assert-single-counts-seq metrics 1)))

  (testing "Testing count recorder returns the results of the underlying java class"
    (let [metrics (metrics/count-recorder clazz)]
      (metrics/increment metrics :test-success)
      (metrics/increment metrics :test-success)
      (metrics/increment metrics :test-success)
      (metrics/add metrics :test-error 3)
      (metrics/add metrics :test-timeout 3)
      (let [{:keys [counts start-millis end-millis]} (first (metrics/counts-seq metrics))]
        (is (= {:test-error 3 :test-success 3 :test-timeout 3} counts))
        (is (>= end-millis start-millis)))
      (assert-single-count-seq metrics 3)
      (assert-single-counts-seq metrics 3)
      (let [{:keys [counts start-millis end-millis] :as swapped} (metrics/counter-swap! metrics)]
        (is (= {:test-error 3 :test-success 3 :test-timeout 3} counts))
        (is (>= end-millis start-millis))
        (assert-single-count-seq metrics 0)
        (assert-single-counts-seq metrics 0)
        (metrics/counter-swap! metrics swapped)
        (is (= {:test-error 0 :test-success 0 :test-timeout 0} (:counts (metrics/counter-swap! metrics)))))))

  (testing "Testing rolling counts return the results of the underlying java class"
    (let [metrics (with-redefs [metrics/current-millis (fn [] 0)]
                    (metrics/rolling-counts clazz 4 1 :seconds))
          ^RollingCounts java-metrics (:precipice-metrics metrics)
          start-millis (:start-millis metrics)]
      (metrics/increment metrics :test-success)
      (metrics/increment metrics :test-error)
      (metrics/increment metrics :test-timeout)
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

(deftest latency-test
  (testing "Testing total latency return the results of the underlying java class"
    (let [metrics (metrics/total-latency clazz)
          ^TotalLatency java-metrics (:precipice-metrics metrics)]
      (.write java-metrics (:test-success key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-error key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-timeout key->enum) 1 1 (System/nanoTime))
      (assert-single-latency-seq metrics {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1})
      (assert-single-latencies-seq metrics {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1})))

  (testing "Testing latency recorder returns the results of the underlying java class"
    (let [metrics (metrics/latency-recorder clazz)
          ^LatencyRecorder java-metrics (:precipice-metrics metrics)]
      (.write java-metrics (:test-success key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-error key->enum) 1 1 (System/nanoTime))
      (.write java-metrics (:test-timeout key->enum) 1 1 (System/nanoTime))
      (assert-single-latencies-seq metrics {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1})
      (assert-single-latency-seq metrics {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1})
      (let [{:keys [latencies start-millis end-millis] :as swapped} (metrics/latency-swap! metrics)]
        (is (= {:test-error {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-success {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}
                :test-timeout {:10 1 :50 1 :90 1 :99 1 :99.9 1 :99.99 1 :99.999 1}} latencies))
        (is (>= end-millis start-millis))
        (assert-single-latencies-seq metrics {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0})
        (assert-single-latency-seq metrics {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0})
        (metrics/latency-swap! metrics swapped)
        (is (= {:test-error {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                :test-success {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}
                :test-timeout {:10 0 :50 0 :90 0 :99 0 :99.9 0 :99.99 0 :99.999 0}}
               (:latencies (metrics/latency-swap! metrics))))))))