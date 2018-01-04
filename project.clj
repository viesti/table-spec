(defproject viesti/table-spec "0.1.1-SNAPSHOT"
  :description "clojure.spec from SQL schema"
  :url "http://github.com/viesti/table-spec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jdbc "0.7.5"]]
  :pedantic? :abort
  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.postgresql/postgresql "42.1.4"]]
                   :plugins [[com.jakemccrary/lein-test-refresh "0.18.1"]]}}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo/",
                                     :creds :gpg}]])
