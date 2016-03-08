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
  (:import (net.uncontended.precipice.metrics RollingCountMetrics)))

(def key->enum (beehive.enums/result-keys->enum {:test-success true
                                                 :test-error false
                                                 :test-timeout false}))

(deftest metrics-test
  (testing "Testing metrics return the results of the underlying java class"
    (let [metrics (metrics/rolling-count-metrics key->enum)
          ^RollingCountMetrics java-metrics (:precipice-metrics (meta metrics))]
      (.incrementMetricCount java-metrics (:test-success key->enum))
      (.incrementMetricCount java-metrics (:test-error key->enum))
      (.incrementMetricCount java-metrics (:test-timeout key->enum))
      (is (= 1 (metrics/total-count metrics :test-success)))
      (is (= 1 (metrics/total-count metrics :test-error)))
      (is (= 1 (metrics/total-count metrics :test-timeout)))

      (is (= 1 (metrics/count-for-period metrics :test-success 1 :minutes)))
      (is (= 1 (metrics/count-for-period metrics :test-error 1 :minutes)))
      (is (= 1 (metrics/count-for-period metrics :test-timeout 1 :minutes))))))