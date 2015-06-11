(ns fault.service
  (:require [fault.future :as f])
  (:import (clojure.lang ILookup)
           (fault ServiceExecutor
                  ResilientAction
                  RejectedActionException
                  ResilientCallback)
           (fault.concurrent ResilientFuture ResilientPromise)
           (fault.circuit CircuitBreaker
                          BreakerConfig
                          BreakerConfig$BreakerConfigBuilder
                          NoOpCircuitBreaker)
           (fault.metrics ActionMetrics DefaultActionMetrics Metric)
           (fault Service)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

(defn- wrap-action-fn [action-fn]
  (reify ResilientAction
    (run [_] (action-fn))))

(defn- wrap-callback-fn [callback-fn]
  (reify ResilientCallback
    (run [_ promise] (callback-fn (f/->CLJResilientFuture promise)))))

(defprotocol CLJService
  (submit-action [this action-fn timeout-millis] [this action-fn callback timeout-millis])
  (perform-action [this action-fn])
  (shutdown [this]))

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
      default))
  Object
  (toString [this]
    (str {:open? (.isOpen breaker)
          :config (let [^BreakerConfig config (.getBreakerConfig breaker)]
                    {:time-period-in-millis (.timePeriodInMillis config)
                     :failure-threshold (.failureThreshold config)
                     :time-to-pause-millis (.timeToPauseMillis config)})})))

(deftype CLJMetrics [^ActionMetrics metrics seconds-tracked]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :errors (.getMetricCountForTimePeriod metrics
                                            Metric/ERROR
                                            seconds-tracked
                                            TimeUnit/SECONDS)
      :successes (.getMetricCountForTimePeriod metrics
                                               Metric/SUCCESS
                                               seconds-tracked
                                               TimeUnit/SECONDS)
      :timeouts (.getMetricCountForTimePeriod metrics
                                              Metric/TIMEOUT
                                              seconds-tracked
                                              TimeUnit/SECONDS)
      :circuit-open (.getMetricCountForTimePeriod metrics
                                                  Metric/CIRCUIT_OPEN
                                                  seconds-tracked
                                                  TimeUnit/SECONDS)
      :queue-full (.getMetricCountForTimePeriod metrics
                                                Metric/QUEUE_FULL
                                                seconds-tracked
                                                TimeUnit/SECONDS)
      :max-concurrency-level-exceeded (.getMetricCountForTimePeriod
                                        metrics
                                        Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED
                                        seconds-tracked
                                        TimeUnit/SECONDS)
      default))
  Object
  (toString [this]
    (str {:errors (.getMetricCountForTimePeriod metrics
                                                Metric/ERROR
                                                seconds-tracked
                                                TimeUnit/SECONDS)
          :successes (.getMetricCountForTimePeriod metrics Metric/SUCCESS
                                                   seconds-tracked
                                                   TimeUnit/SECONDS)
          :timeouts (.getMetricCountForTimePeriod metrics
                                                  Metric/TIMEOUT
                                                  seconds-tracked
                                                  TimeUnit/SECONDS)
          :circuit-open (.getMetricCountForTimePeriod metrics
                                                      Metric/CIRCUIT_OPEN
                                                      seconds-tracked
                                                      TimeUnit/SECONDS)
          :queue-full (.getMetricCountForTimePeriod metrics
                                                    Metric/QUEUE_FULL
                                                    seconds-tracked
                                                    TimeUnit/SECONDS)
          :max-concurrency-level-exceeded (.getMetricCountForTimePeriod
                                            metrics
                                            Metric/MAX_CONCURRENCY_LEVEL_EXCEEDED
                                            seconds-tracked)})))

(deftype CLJServiceImpl
  [^ServiceExecutor executor ^CLJMetrics metrics ^CLJBreaker breaker]
  CLJService
  (submit-action [this action-fn timeout-millis]
    (submit-action this action-fn nil timeout-millis))
  (submit-action [_ action-fn callback timeout-millis]
    (try
      (f/->CLJResilientFuture
        (.promise ^ResilientFuture
                  (if callback
                    (.submitAction executor
                                   ^ResilientAction (wrap-action-fn action-fn)
                                   ^ResilientCallback (wrap-callback-fn callback)
                                   (long timeout-millis))
                    (.submitAction executor
                                   ^ResilientAction (wrap-action-fn action-fn)
                                   (long timeout-millis)))))
      (catch RejectedActionException e
        (f/rejected-action-future (.reason e)))))
  (perform-action [_ action-fn]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.performAction executor (wrap-action-fn action-fn)))
         (catch RejectedActionException e
           (f/rejected-action-future (.reason e)))))
  (shutdown [_] (.shutdown executor))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :metrics metrics
      :service-executor executor
      :breaker breaker
      default)))

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

(defn close-circuit! [^CLJServiceImpl service]
  (.forceClosed ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn open-circuit! [^CLJServiceImpl service]
  (.forceOpen ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn service-executor [name pool-size max-concurrency {:keys [seconds]}]
  (let [metrics (DefaultActionMetrics. seconds 1 TimeUnit/SECONDS)
        executor (Service/defaultService name (int pool-size) (int max-concurrency) metrics)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor) seconds)
                      (->CLJBreaker (.getCircuitBreaker executor)))))

(defn executor-with-no-opt-breaker
  [name pool-size max-concurrency {:keys [seconds]}]
  (let [breaker (NoOpCircuitBreaker.)
        metrics (DefaultActionMetrics. seconds 1 TimeUnit/SECONDS)
        executor (Service/defaultService name
                                         (int pool-size)
                                         (int max-concurrency)
                                         metrics
                                         breaker)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor) seconds)
                      (->CLJBreaker (.getCircuitBreaker executor)))))
