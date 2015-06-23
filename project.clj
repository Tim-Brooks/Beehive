(defproject net.uncontended/beehive "0.2.0"
  :description "Beehive is a Clojure facade for the Precipice library."
  :url "https://github.com/tbrooks8/Beehive"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                                  [clj-http "1.0.1"]
                                  [criterium "0.4.3"]]}}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [net.uncontended/Precipice "0.1.0"]])


