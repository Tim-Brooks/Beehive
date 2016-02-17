(ns beehive.semaphore
  (:import (net.uncontended.precipice Rejected)
           (net.uncontended.precipice.semaphore LongSemaphore
                                                PrecipiceSemaphore
                                                UnlimitedSemaphore)))

(set! *warn-on-reflection* true)

(defn max-concurrency-level [semaphore]
  (.maxConcurrencyLevel ^PrecipiceSemaphore semaphore))

(defn remaining-capacity [semaphore]
  (.remainingCapacity ^PrecipiceSemaphore semaphore))

(defn current-concurrency-level [semaphore]
  (.currentConcurrencyLevel ^PrecipiceSemaphore semaphore))

(defn semaphore [max-concurrency]
  (LongSemaphore. Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED (long max-concurrency)))

(defn unlimited-semaphore [max-concurrency]
  (UnlimitedSemaphore. Rejected/MAX_CONCURRENCY_LEVEL_EXCEEDED))