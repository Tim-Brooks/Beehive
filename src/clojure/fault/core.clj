(ns fault.core
  (:require [fault.service :as s]))

(set! *warn-on-reflection* true)

(when
  (try
    (require '[clojure.core.async])
    true
    (catch Exception _
      false))
  (do (require '[fault.async])))

(defn service
  [name pool-size max-concurrency
   & {:keys [breaker metrics]
      :or {breaker {} metrics {:seconds 3600}}}]
  (if (empty? breaker)
    (s/executor-with-no-opt-breaker name pool-size max-concurrency metrics)
    (s/service-executor name pool-size max-concurrency breaker metrics)))

(defn metrics [{:keys [metrics]}]
  (let [{:keys [failures
                errors
                successes
                timeouts
                circuit-open
                queue-full
                max-concurrency-level-exceeded]} metrics]
    {:failures failures
     :errors errors
     :successes successes
     :timeouts timeouts
     :circuit-open circuit-open
     :queue-full queue-full
     :max-concurrency-level-exceeded max-concurrency-level-exceeded}))

(defn submit-action [service f time-out-ms]
  (s/submit-action service f time-out-ms))

(defn perform-action [service f]
  (s/perform-action service f))
