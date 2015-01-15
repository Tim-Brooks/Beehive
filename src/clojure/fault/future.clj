(ns fault.future
  (:import (fault ResilientPromise Status RejectedReason)
           (clojure.lang IDeref IBlockingDeref IPending ILookup)))

(set! *warn-on-reflection* true)

(defn- status [status-enum]
  (cond
    (= Status/PENDING status-enum) :pending
    (= Status/SUCCESS status-enum) :success
    (= Status/ERROR status-enum) :error
    (= Status/TIMED_OUT status-enum) :timed-out))

(deftype CLJResilientFuture [^ResilientPromise promise]
  IDeref
  (deref [this] (or (.awaitResult promise) (.getError promise) (:status this)))
  IBlockingDeref
  (deref
    [this timeout-ms timeout-val]
    (if (.await promise timeout-ms)
      (or (.getResult promise) (.getError promise) (:status this))
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

(deftype CLJRejectedFuture [reason]
  IDeref
  (deref [this] reason)
  IBlockingDeref
  (deref [this timeout-ms timeout-val] reason)
  IPending
  (isRealized [_]
    true)
  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [_ key default]
    (case key
      :status :rejected
      :rejected-reason reason
      default)))

(def ^:private reject-enum->keyword
  {RejectedReason/CIRCUIT_OPEN :circuit-open
   RejectedReason/MAX_CONCURRENCY_LEVEL_EXCEEDED :max-concurrency-level-exceeded
   RejectedReason/QUEUE_FULL :queue-full})

(defn rejected-action-future [reason]
  (->CLJRejectedFuture (get reject-enum->keyword reason)))