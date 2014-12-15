(ns fault.core
  (:require [fault.promise :as p]
            [fault.service :as s])
  (:import (fault ServiceExecutor ResilientAction)))

(defn service [pool-size]
  (s/service-executor pool-size))

(defn perform-action [^ServiceExecutor service f time-out-ms]
  (p/->CLJResilientPromise
    (.performAction service
                    (reify ResilientAction
                      (run [_] (f)))
                    time-out-ms)))