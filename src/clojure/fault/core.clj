(ns fault.core
  (:require fault.promise :as p)
  (:import (fault ServiceExecutor ResilientAction)))

(defn service-executor [num-of-threads]
  (ServiceExecutor. num-of-threads))

(defn resilient-action [f]
  (reify ResilientAction
    (run [_] (f))))