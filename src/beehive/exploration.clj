(ns beehive.exploration
  (:import (net.uncontended.precipice Failable)))

(def thing
  (gen-class
    :name clojure.examples.impl
    :extends Enum
    :prefix "test-"
    :methods []))

