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
  (:import (net.uncontended.precipice.metrics RollingCountMetrics)
           (net.uncontended.precipice.result TimeoutableResult)))

(deftest metrics-test
  (testing "Testing metrics return the results of the underlying java class"
    (let [^RollingCountMetrics metrics (metrics/count-metrics)]
      (.incrementMetricCount metrics TimeoutableResult/SUCCESS)
      (.incrementMetricCount metrics TimeoutableResult/ERROR)
      (.incrementMetricCount metrics TimeoutableResult/TIMEOUT)
      (is (= 1 (metrics/total-count metrics :success)))
      (is (= 1 (metrics/total-count metrics :timeout)))
      (is (= 1 (metrics/total-count metrics :error)))

      (is (= 1 (metrics/count-for-period metrics :success 1 :minutes)))
      (is (= 1 (metrics/count-for-period metrics :timeout 1 :minutes)))
      (is (= 1 (metrics/count-for-period metrics :error 1 :minutes))))))