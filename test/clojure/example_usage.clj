(ns clojure.example-usage
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! go]]
            [clj-http.client :as http]
            [fault.core :as fault]
            [fault.service :as service]))

(def api-route (str "http://www.broadbandmap.gov/broadbandmap/"
                    "census/county/%s?format=json"))

(def county-service (fault/service 5 30))

(defn lookup-state-action [county]
  (fn [] (-> (http/get (format api-route county) {:as :json})
             :body
             :Results
             :county)))

(defn thing [in-channel out-channel]
  (go
    (loop []
      (let [county (<! in-channel)]
        (>! out-channel (service/submit-action county-service
                                               (lookup-state-action county)
                                               1000))
        (recur)))))

(defn run [county]
  (let [in-channel (async/chan 1)
        out-channel (async/chan 1)]
    (>!! in-channel county)
    (<!! out-channel)))


