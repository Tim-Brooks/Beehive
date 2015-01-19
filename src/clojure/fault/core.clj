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

(defn service [pool-size max-concurrency]
  (s/service-executor pool-size max-concurrency))

(defn submit-action [service f time-out-ms]
  (s/submit-action service f time-out-ms))

(defn perform-action [service f]
  (s/perform-action service f))
