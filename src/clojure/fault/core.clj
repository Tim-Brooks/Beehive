(ns fault.core
  (:require [fault.future :as f]
            [fault.service :as s])
  (:import (fault ServiceExecutor ResilientAction ResilientFuture ResilientPromise)))

(set! *warn-on-reflection* true)

(defn service [pool-size max-concurrency]
  (s/service-executor pool-size max-concurrency))

(defn submit-action [service f time-out-ms]
  (f/->CLJResilientFuture
    (.promise ^ResilientFuture (s/submit-action service f time-out-ms))))

(defn perform-action [service f]
  (f/->CLJResilientFuture
    ^ResilientPromise (s/perform-action service f)))