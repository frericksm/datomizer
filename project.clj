(defproject com.goodguide/datomizer "0.2.0"
  :description "Simple Datomic adapter and marshalling for JRuby"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.datomic/datomic-pro "[0.9,1.0)"]
                 [org.clojure/test.check "0.5.7"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]]}}
  :jvm-opts ["-Xmx1g"])
