(ns fault.promise
  (:import (fault ResilientPromise Status)
           (clojure.lang IDeref IBlockingDeref IPending ILookup)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (= Status/PENDING status-enum) :pending
    (= Status/SUCCESS status-enum) :success
    (= Status/ERROR status-enum) :error
    (= Status/TIMED_OUT status-enum) :timed-out))

(deftype CLJResilientPromise [^ResilientPromise promise]
  IDeref
  (deref [_] (or (.awaitResult promise) (.getError promise) :timed-out))
  IBlockingDeref
  (deref
    [_ timeout-ms timeout-val]
    (if (.await promise timeout-ms)
      (or (.getResult promise) (.getError promise) :timed-out)
      timeout-val))
  IPending
  (isRealized [_]
    (.isDone promise))
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status (status (.getStatus promise))
      :result (.getResult promise)
      :error (.getError promise)
      default)))
