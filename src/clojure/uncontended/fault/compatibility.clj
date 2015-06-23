(ns uncontended.fault.compatibility
  (:require [uncontended.fault.future :as f])
  (:import (net.uncontended.precipice ResilientPatternAction
                                      ResilientAction
                                      ResilientCallback)))

(defn wrap-pattern-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context] (action-fn context))))

(defn wrap-action-fn [action-fn]
  (reify ResilientAction
    (run [_] (action-fn))))

(defn wrap-callback-fn [callback-fn]
  (reify ResilientCallback
    (run [_ promise] (callback-fn (f/->CLJResilientFuture promise)))))