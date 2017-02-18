(ns table-spec.fixture
  (:require  [clojure.test :refer :all]
             [clojure.string :as str]
             [clojure.java.shell :refer [sh]]
             [clojure.java.jdbc :as jdbc]))

(defn sh! [& args]
  (let [{:keys [exit out]} (apply sh args)]
    (when-not (zero? exit)
      (throw (Exception. (str "'" (str/join " " args) "' returned " exit))))
    (.trim out)))

(defn db-available? [uri]
  (try
    (jdbc/with-db-connection [db {:connection-uri uri}])
    true
    (catch Throwable t
      false)))

(defn wait-for-db [uri]
  (loop [count 0
         available? false]
    (if (and (not available?)
             (< count 10))
      (if-not (db-available? uri)
        (do
          (Thread/sleep 1000)
          (recur (inc count) false))
        (recur (inc count) true))
      (when (>= count 10)
        (throw (Exception. "Database not available"))))))

(defn with-db [uri f]
  (let [id (sh! "docker" "run"
                "--name" "table-spec"
                "-e" "POSTGRES_PASSWORD=secret"
                "-d"
                "-p" "5433:5432"
                "postgres:9.6.2-alpine")]
    (wait-for-db uri)
    (f)
    (sh! "docker" "stop" id)
    (sh "docker" "rm" "table-spec")))
