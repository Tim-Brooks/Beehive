(ns fault.promise
  (:import (fault ResilientPromise Status)
           (clojure.lang IDeref IBlockingDeref IPending ILookup)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (= Status/PENDING status-enum) :pending
    (= Status/SUCCESS status-enum) :success
    (= Status/ERROR status-enum) :error
    (= Status/TIMED_OUT status-enum) :time-out))

(deftype CLJResilientPromise [^ResilientPromise promise]
  IDeref
  (deref [_] (.awaitResult promise))
  IBlockingDeref
  (deref
    [_ timeout-ms timeout-val]
    (if (.await promise timeout-ms)
      (.result promise)
      timeout-val)) 
  IPending
  (isRealized [_]
    (.isDone promise))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status (status (.status promise))
      :result (.result promise)
      :error (.error promise)
      default)))
