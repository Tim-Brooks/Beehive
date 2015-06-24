(ns beehive.utils
  (:import (java.util.concurrent TimeUnit)))

(def time-units {:milliseconds TimeUnit/MILLISECONDS
                 :seconds TimeUnit/SECONDS
                 :minutes TimeUnit/MINUTES
                 :hours TimeUnit/HOURS
                 :days TimeUnit/DAYS})

(defn ->time-unit [unit-in-keyword]
  (if-let [time-unit (get time-units unit-in-keyword)]
    time-unit
    (throw
      (IllegalArgumentException.
        ^String
        (format "Invalid time unit argument: %s. Valid arguments are: %s."
                unit-in-keyword
                (vec (keys time-units)))))))