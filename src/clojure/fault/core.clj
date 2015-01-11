(ns fault.core
  (:require [fault.future :as f]
            [fault.service :as s])
  (:import (fault ServiceExecutor ResilientAction ResilientFuture ResilientPromise)))

(set! *warn-on-reflection* true)

(defn service [pool-size]
  (s/service-executor pool-size))

(defn submit-action [^ServiceExecutor service f time-out-ms]
  (f/->CLJResilientFuture
    (.promise ^ResilientFuture (.submitAction service
                                              (reify ResilientAction
                                                (run [_] (f)))
                                              time-out-ms))))

(defn perform-action [^ServiceExecutor service f]
  (f/->CLJResilientFuture
    ^ResilientPromise (.performAction service
                                      (reify ResilientAction
                                        (run [_] (f))))))