(defproject fault "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[junit/junit "4.11"]]}}
  :javac-target "1.7"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.mockito/mockito-core "1.10.8"]
                 [criterium "0.4.3"]]
  :source-paths ["src/clojure"]
  :junit ["test/java"]
  :java-source-paths ["src/java" "test/java"]
  :plugins [[lein-junit "1.1.3"]]
  :junit-formatter "brief")
