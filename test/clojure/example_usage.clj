(ns clojure.example-usage
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! go]]
            [clj-http.client :as http]
            [fault.core :as fault]
            [fault.service :as service]))

(def api-route (str "http://www.broadbandmap.gov/broadbandmap/"
                    "census/county/%s?format=json"))

(defonce county-service (fault/service 5 30))

(defn lookup-state-action [county]
  (fn [] (-> (http/get (format api-route county) {:as :json})
             :body
             :Results
             :county)))

(defn hello [in-channel]
  (go (loop []
        (println "Success " (<! in-channel))
        (recur))))

(defn handle-error [err-channel]
  (go (loop []
        (println "Error " (<! err-channel))
        (recur))))

(defn thing [in-channel out-channel err-channel]
  (go
    (loop []
      (let [county (<! in-channel)]
        (service/submit-action county-service
                               (lookup-state-action county)
                               (fn [future]
                                 (if (:success? future)
                                   (>!! out-channel future)
                                   (>!! err-channel future)))
                               10)
        (recur)))))

(defn run []
  (let [in-channel (async/chan 10)
        out-channel (async/chan 10)
        err-channel (async/chan 10)]
    (thing in-channel out-channel err-channel)
    (hello out-channel)
    (handle-error err-channel)
    in-channel))


