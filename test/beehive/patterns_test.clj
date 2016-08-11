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

(ns beehive.patterns-test
  (:require [clojure.test :refer :all]
            [beehive.patterns :as patterns]
            [beehive.hive :as hive]
            [beehive.metrics :as metrics]
            [beehive.semaphore :as semaphore]))

;(set! *warn-on-reflection* true)

(def beehive1 nil)
(def beehive2 nil)
(def beehive3 nil)

(defn- beehive-name [context]
  (get-in context [:beehive :name]))

(defn- create-beehive [_ name address]
  (->
    (hive/lett [result-class {:test-success true :test-error false}
                rejected-class #{:max-concurrency}]
      (-> (hive/hive name result-class rejected-class)
          (hive/set-result-metrics (metrics/count-metrics result-class))
          (hive/set-rejected-metrics (metrics/count-metrics rejected-class))
          (hive/add-backpressure
            :semaphore (semaphore/semaphore 1 :max-concurrency))
          hive/map->hive))
    (assoc :context {:address address})))

(defn- create-hives [f]
  (alter-var-root #'beehive1 #(create-beehive % "Test1" "www.service1.com"))
  (alter-var-root #'beehive2 #(create-beehive % "Test2" "www.service2.com"))
  (alter-var-root #'beehive3 #(create-beehive % "Test3" "www.service3.com"))
  (f))

(use-fixtures :each create-hives)


(deftest load-balancer-test
  (testing "Acquire attempts are distributed properly."
    (let [load-balancer (patterns/load-balancer [beehive1 beehive2 beehive3])]
      (is (= #{"Test1" "Test2" "Test3"}
             (set (map beehive-name
                       (concat (patterns/pattern-seq load-balancer)
                               (patterns/pattern-seq load-balancer)
                               (patterns/pattern-seq load-balancer))))))))
  (doseq [b [beehive1 beehive2 beehive3]]
    (hive/release-raw-permits b 1))
  (testing "Individual rejections will be handled."
    (let [context (hive/acquire beehive1 1)
          load-balancer (patterns/load-balancer [beehive1 beehive2 beehive3])]
      (is (not= ["Test1"] (map beehive-name (patterns/pattern-seq load-balancer))))
      (is (not= ["Test1"] (map beehive-name (patterns/pattern-seq load-balancer))))
      (hive/release-without-result beehive3 context)
      (is (= ["Test3"] (map beehive-name (patterns/pattern-seq load-balancer))))
      (doseq [b [beehive1 beehive2 beehive3]]
        (hive/release-raw-permits b 1)))))

(deftest shotgun-test
  (testing "Acquire attempts are distributed to multiple beehives."
    (let [shotgun (patterns/shotgun [beehive1 beehive2 beehive3] 2)
          beehives (atom #{})]
      (doseq [_ (range 20)]
        (let [contexts (patterns/pattern-seq shotgun)]
          (is (= 2 (count (set (map beehive-name contexts)))))
          (doseq [context contexts]
            (swap! beehives conj (beehive-name context))
            (hive/release-without-result (:beehive context) context 1))))
      (is (= @beehives (set (map :name [beehive1 beehive2 beehive3])))))))
