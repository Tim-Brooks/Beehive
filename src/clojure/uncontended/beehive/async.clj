(ns uncontended.beehive.async
  (:require [clojure.core.async :refer [>!!]]))


(defn return-channels [{:keys [success timed-out error failed any]}]
  (fn [future]
    (when any
      (>!! any future))
    (condp = (:status future)
      :success (when success (>!! success future))
      :timed-out (do (when timed-out (>!! timed-out future))
                     (when failed (>!! failed future)))
      :error (do (when error (>!! error future))
                 (when failed (>!! failed future))))))