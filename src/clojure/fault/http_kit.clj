(ns fault.http-kit
  (:require [org.httpkit.client :as client]
            [fault.service :as service])
  (:import (fault HttpKitExecutor ResilientAction)))

(set! *warn-on-reflection* true)

(def ^:private service (service/service-executor 1 30))

(defn request [^HttpKitExecutor service request-map callback]
  (let [modified-map (-> request-map
                         (assoc :worker-pool (.callbackExecutor service))
                         (assoc :client (.client service)))]
    (.submitAction service
                   (reify ResilientAction
                     (run [_]
                       (client/request modified-map callback)))
                   0)))
