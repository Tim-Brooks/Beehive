(ns uncontended.fault.service
  (:require [uncontended.fault.compatibility :as c]
            [uncontended.fault.future :as f]
            [uncontended.fault.utils :as utils])
  (:import (clojure.lang ILookup)
           (net.uncontended.precipice Services
                                      ResilientAction
                                      RejectedActionException
                                      ResilientCallback
                                      Service)
           (net.uncontended.precipice.concurrent ResilientFuture ResilientPromise)
           (net.uncontended.precipice.circuit CircuitBreaker
                                              BreakerConfig
                                              BreakerConfigBuilder
                                              NoOpCircuitBreaker)
           (net.uncontended.precipice.metrics ActionMetrics
                                              DefaultActionMetrics
                                              Metric)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

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
                {:time-period-in-millis (.trailingPeriodMillis config)
                 :failure-threshold (.failureThreshold config)
                 :time-to-pause-millis (.backOffTimeMillis config)})
      default))
  Object
  (toString [this]
    (str {:open? (.isOpen breaker)
          :config (let [^BreakerConfig config (.getBreakerConfig breaker)]
                    {:trailing-period-millis (.trailingPeriodMillis config)
                     :failure-threshold (.failureThreshold config)
                     :back-off-time-millis (.backOffTimeMillis config)
                     :failure-percentage-threshold (.failurePercentageThreshold config)
                     :health-refresh-millis (.healthRefreshMillis config)})})))

(deftype CLJMetrics [^ActionMetrics metrics seconds-tracked]
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :snapshot (.snapshot metrics seconds-tracked TimeUnit/SECONDS)
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
                                            TimeUnit/SECONDS)})))

(deftype CLJServiceImpl
  [^Service executor ^CLJMetrics metrics ^CLJBreaker breaker]
  CLJService
  (submit-action [this action-fn timeout-millis]
    (submit-action this action-fn nil timeout-millis))
  (submit-action [_ action-fn callback timeout-millis]
    (try
      (f/->CLJResilientFuture
        (.promise ^ResilientFuture
                  (if callback
                    (.submitAction executor
                                   ^ResilientAction (c/wrap-action-fn action-fn)
                                   ^ResilientCallback (c/wrap-callback-fn callback)
                                   (long timeout-millis))
                    (.submitAction executor
                                   ^ResilientAction (c/wrap-action-fn action-fn)
                                   (long timeout-millis)))))
      (catch RejectedActionException e
        (f/rejected-action-future (.reason e)))))
  (perform-action [_ action-fn]
    (try (f/->CLJResilientFuture
           ^ResilientPromise (.performAction executor (c/wrap-action-fn action-fn)))
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
   {:keys [trailing-period-millis
           failure-threshold
           failure-percentage-threshold
           backoff-time-millis
           health-refresh-millis]}]
  (.setBreakerConfig
    ^CircuitBreaker circuit-breaker
    ^BreakerConfig (doto (BreakerConfigBuilder.)
                     (.trailingPeriodMillis trailing-period-millis)
                     (.failureThreshold failure-threshold)
                     (.failurePercentageThreshold failure-percentage-threshold)
                     (.backOffTimeMillis backoff-time-millis)
                     (.healthRefreshMillis health-refresh-millis)
                     (.build))))

(defn close-circuit! [^CLJServiceImpl service]
  (.forceClosed ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn open-circuit! [^CLJServiceImpl service]
  (.forceOpen ^CircuitBreaker (.breaker ^CLJBreaker (.breaker service))))

(defn service-executor
  [name
   pool-size
   max-concurrency
   {:keys [failure-percentage-threshold backoff-time-millis]}
   {:keys [slots-to-track resolution time-unit]}]
  (let [metrics (DefaultActionMetrics. slots-to-track resolution (utils/->time-unit time-unit))
        executor (Services/defaultService ^String name
                                          (int pool-size)
                                          (int max-concurrency)
                                          metrics)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor)
                                    (.convert TimeUnit/SECONDS
                                              slots-to-track
                                              (utils/->time-unit time-unit)))
                      (->CLJBreaker (.getCircuitBreaker executor)))))

(defn executor-with-no-opt-breaker
  [name pool-size max-concurrency {:keys [slots-to-track resolution time-unit]}]
  (let [breaker (NoOpCircuitBreaker.)
        metrics (DefaultActionMetrics. slots-to-track
                                       resolution
                                       (utils/->time-unit time-unit))
        executor (Services/defaultService name
                                          (int pool-size)
                                          (int max-concurrency)
                                          metrics
                                          breaker)]
    (->CLJServiceImpl executor
                      (->CLJMetrics (.getActionMetrics executor)
                                    (.convert TimeUnit/SECONDS
                                              slots-to-track
                                              (utils/->time-unit time-unit)))
                      (->CLJBreaker (.getCircuitBreaker executor)))))
