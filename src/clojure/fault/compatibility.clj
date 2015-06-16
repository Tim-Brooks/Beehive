(ns fault.compatibility
  (:import (fault ResilientPatternAction)))

(defn wrap-pattern-action-fn [action-fn]
  (reify ResilientPatternAction
    (run [_ context] (action-fn context))))