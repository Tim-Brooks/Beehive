(ns fault.http-kit
  (:require [org.httpkit.client :as client]
            [fault.service :as service])
  (:import (fault ServiceExecutor ResilientAction ResilientCallback ResilientFuture ResilientPromise)))


(def ^:private service (service/service-executor 1 30))

(defn thing []
  (service/submit-action
    service
    (fn [] (client/get "http://www.google.com")) 300))