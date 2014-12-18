(ns fault.service
  (:import (fault ServiceExecutor)
           (fault.circuit CircuitBreaker BreakerConfig)
           (clojure.lang ILookup)))

(deftype CLJBreaker [^CircuitBreaker breaker]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :open? (.isOpen breaker)
      :config (let [^BreakerConfig config (.getBreakerConfig breaker)]
                {:time-period-in-millis (.timePeriodInMillis config)
                 :failure-threshold (.failureThreshold config)
                 :time-to-pause-millis (.timeToPauseMillis config)})
      default)))

(defn service-executor [pool-size]
  (let [executor (ServiceExecutor. pool-size)]
    {:service executor
     :metrics (.getActionMetrics executor)
     :circuit-breaker (.getCircuitBreaker executor)}))
