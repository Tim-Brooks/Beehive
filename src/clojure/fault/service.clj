(ns fault.service
  (:import (fault ServiceExecutor)
           (fault.circuit CircuitBreaker BreakerConfig BreakerConfig$BreakerConfigBuilder)
           (clojure.lang ILookup)
           (fault.metrics ActionMetrics)))

(set! *warn-on-reflection* true)

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

(deftype CLJMetrics [^ActionMetrics metrics])

(defn swap-breaker-config!
  [{:keys [circuit-breaker]}
   {:keys [time-period-in-millis failure-threshold time-to-pause-millis]}]
  (.setBreakerConfig
    ^CircuitBreaker circuit-breaker
    ^BreakerConfig (doto (BreakerConfig$BreakerConfigBuilder.)
                     (.timePeriodInMillis time-period-in-millis)
                     (.failureThreshold failure-threshold)
                     (.timeToPauseMillis time-to-pause-millis)
                     (.build))))

(defn service-executor [pool-size]
  (let [executor (ServiceExecutor. pool-size)]
    {:service executor
     :metrics (.getActionMetrics executor)
     :circuit-breaker (->CLJBreaker (.getCircuitBreaker executor))}))
