(ns table-spec.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.spec.gen.alpha :as gen])
  (:import [java.sql Types]))

(defmulti data-type :data_type)

(defmethod data-type Types/INTEGER [_]
  (s/spec int?))

(defmethod data-type Types/TIMESTAMP [_]
  (s/spec #(instance? java.sql.Timestamp %)
          :gen (fn []
                 (gen/fmap #(java.sql.Timestamp. ^Long %)
                           (gen/large-integer)))))

(defmethod data-type Types/VARCHAR [{:keys [column_size]}]
  (s/spec (s/and string?
                 #(<= (.length %) column_size))))

(defmethod data-type Types/BIT [_]
  (s/spec boolean?))

(defmethod data-type Types/BOOLEAN [_]
  (s/spec boolean?))

(defmethod data-type Types/DOUBLE [_]
  (s/spec double?))

(defmethod data-type Types/FLOAT [_]
  (s/spec double?))

(defmethod data-type Types/REAL [_]
  (s/spec double?))

(defmethod data-type Types/CHAR [_]
  (s/spec char?))

(defmethod data-type Types/NUMERIC [{:keys [decimal_digits]}]
  (s/spec decimal?
          :gen (fn []
                 (gen/fmap #(.setScale (BigDecimal/valueOf ^Double %)
                                       decimal_digits java.math.RoundingMode/UP)
                           (gen/double* {:infinite? false :NaN? false})))))

(defmethod data-type :default [m]
  (throw (Exception. (str "Undefined data type: " (:data_type m)))))

(defn table-meta [md schema]
  (-> md
      (.getColumns nil schema nil nil)
      (jdbc/metadata-result)
      (#(group-by :table_name %))))

(defn tables [{:keys [schema] :as db-spec}]
  (jdbc/with-db-metadata [md db-spec]
    (for [[table columns] (table-meta md schema)]
      (reduce (fn [acc {:keys [column_name] :as column}]
                (let [k (keyword table column_name)]
                  (-> acc
                      (update :specs assoc k (data-type column))
                      (update :opts #(if (= "NO" (:is_nullable column))
                                       (update % :req conj k)
                                       %)))))
              {:table table
               :specs {}
               :opts {:req #{}}}
              columns))))

(defn register [table]
  (doseq [{:keys [table specs opts]} table]
    (doseq [[k s] specs]
      (eval `(s/def ~k ~s)))
    (let [required-keys# (-> opts :req vec)
          optional-keys# (-> specs keys set (set/difference required-keys#) vec)]
      (eval `(s/def ~(keyword "table" table) (s/keys :req ~required-keys#
                                                     :opt ~optional-keys#))))))
