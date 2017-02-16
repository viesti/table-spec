(ns table-spec.core-test
  (:require [clojure.test :refer :all]
            [table-spec.core :as sut]
            [clojure.java.shell :refer [sh]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.spec :as s]))

#_(def connection-uri "jdbc:postgresql://localhost:5433/postgres?user=postgres&password=secret")
(def connection-uri "jdbc:postgresql:test")

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

(defn with-db [f]
  (let [id (sh! "docker" "run"
                "--name" "table-spec"
                "-e" "POSTGRES_PASSWORD=secret"
                "-d"
                "-p" "5433:5432"
                "postgres:9.6.2-alpine")]
    (wait-for-db connection-uri)
    (f)
    (sh! "docker" "stop" id)
    (sh! "docker" "rm" "table-spec")))

#_(use-fixtures :once with-db)

(defmacro with-state [state & body]
  `(do
     (jdbc/with-db-connection [db# {:connection-uri (:connection-uri ~state)}]
       (jdbc/execute! db# (:up ~state)))
     (try
       ~@body
       (finally
         (jdbc/with-db-connection [db# {:connection-uri (:connection-uri ~state)}]
           (jdbc/execute! db# (:down ~state)))))))

(defn map-value [f coll]
  (into {} (for [[k v] coll] [k (f v)])))

(defn assert-spec [expected]
  (let [tables (sut/tables {:connection-uri connection-uri
                            :schema "public"})
        table-name (:table (first tables))
        specs (-> tables first :specs)]
    (is (= (map-value s/form expected)
           (map-value s/form specs)))
    (sut/register tables)
    (jdbc/with-db-connection [db {:connection-uri connection-uri}]
      (jdbc/insert-multi! db table-name (map first (s/exercise (keyword "table" table-name))))
      (is (= 10
             (jdbc/query db
                         [(str "select count(*) as count from " table-name)]
                         {:row-fn :count
                          :result-set-fn first}))))
    specs))

(deftest one-not-null-column
  (with-state {:up ["create table foo (id int not null)"]
               :down ["drop table foo"]
               :connection-uri connection-uri}
    (let [[{:keys [specs opts]}] (sut/tables {:connection-uri connection-uri
                                              :schema "public"})]
      (is (= {:foo/id (s/form (s/spec int?))}
             (map-value s/form specs)))
      (is (= #{:foo/id} (-> opts :req set))))))

(deftest many-columns
  (with-state {:up ["create table foo (id int not null,
                                       ts timestamp,
                                       name varchar(250))"]
               :down ["drop table foo"]
               :connection-uri connection-uri}
    (let [column_size 250
          specs (assert-spec {:foo/id (s/spec int?)
                              :foo/ts (s/spec #(instance? java.sql.Timestamp %))
                              :foo/name (s/spec (s/and string?
                                                       #(<= (.length %) column_size)))})]
      (is (s/valid? (:foo/name specs)
                    (String. (byte-array 250) "UTF-8")))
      (is (not (s/valid? (:foo/name specs)
                         (String. (byte-array 251) "UTF-8")))))))
