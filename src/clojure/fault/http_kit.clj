(ns fault.http-kit
  (:require [org.httpkit.client :as client]
            [fault.service :as service])
  (:import (fault HttpKitExecutor)))

(set! *warn-on-reflection* true)

(def ^:private service (service/service-executor 1 30))

(defn request [^HttpKitExecutor service request-map callback]
  (let [modified-map (-> request-map
                         (assoc :worker-pool (.callbackExecutor service)))]
    (client/request request-map callback)))

(defn thing []
  (service/submit-action
    service
    (fn [] (client/get "http://www.google.com")) 300))