(defproject fault "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[junit/junit "4.11"]
                                  [org.mockito/mockito-core "1.10.8"]
                                  [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                                  [clj-http "1.0.1"]
                                  [criterium "0.4.3"]]
                   :junit ["test/java"]
                   :java-source-paths ["src/java" "test/java"]
                   :plugins [[lein-junit "1.1.3"]]
                   :junit-formatter "brief"}}
  :javac-target "1.7"
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"])


