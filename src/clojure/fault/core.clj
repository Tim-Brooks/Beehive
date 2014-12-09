(ns fault.core
  (:import (fault ServiceExecutor ResilientAction)))


(defn service-executor [num-of-threads]
  (ServiceExecutor. num-of-threads))

(defn resilient-action [f]
  (reify ResilientAction
    (run [_] (f))))