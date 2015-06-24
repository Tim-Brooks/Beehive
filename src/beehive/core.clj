(ns beehive.core
  (:require [beehive.service :as s]))

(set! *warn-on-reflection* true)

(when
  (try
    (require '[clojure.beehive.async])
    true
    (catch Exception _
      false))
  (do (require '[uncontended.fault.async])))

(defn service
  [name pool-size max-concurrency
   & {:keys [breaker metrics]
      :or {breaker {} metrics {:slots-to-track 3600
                               :resolution 1
                               :time-unit :seconds}}}]
  (if (empty? breaker)
    (s/executor-with-no-opt-breaker name pool-size max-concurrency metrics)
    (s/service-executor name pool-size max-concurrency breaker metrics)))

(defn metrics [{:keys [metrics]}]
  (-> metrics :snapshot))

(defn submit-action [service f time-out-ms]
  (s/submit-action service f time-out-ms))

(defn perform-action [service f]
  (s/perform-action service f))
