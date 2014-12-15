(ns fault.service
  (:import (fault ServiceExecutor)))

(defn service-executor [pool-size]
  (let [executor (ServiceExecutor. pool-size)]
    {:service executor
     :metrics (.getActionMetrics executor)
     :circuit-breaker (.getCircuitBreaker executor)}))
