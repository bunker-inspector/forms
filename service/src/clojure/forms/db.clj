(ns forms.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [honey.sql :as sql]
            [integrant.core :as ig]
            [inflections.core :as inflections])
  (:import com.zaxxer.hikari.HikariDataSource))

(defmethod ig/init-key :db/connection [_ db-spec]
  (connection/->pool HikariDataSource
                     (assoc db-spec
                            :username (:user db-spec)
                            :connectionInitSql "COMMIT;"
                            :dataSourceProperties {:socketTimeout 30})))

(defmethod ig/halt-key! :db/connection [_ connection]
  (.close connection))

(defn- clj-ify [result & {preserve-namespaces? :preserve-namespaces?
                          :or {preserve-namespaces? false}}]
  (some->> result
           vec
           (map (fn [[a b]]
                  [(-> a
                       ((if preserve-namespaces? identity (comp keyword name)))
                       inflections/hyphenate) b]))
           (into {})))

(defn execute-one! [conn query-map]
  (jdbc/execute-one! conn
                     (sql/format query-map)
                     {:return-keys :tre}))

(defn fetch [conn table value
             & {by-column :by
                :or {by-column :id}}]
  {:pre [(some? table)
         (some? value)]}
  (->
   conn
   (execute-one! {:select [:*]
                  :from [table]
                  :where [:= by-column value]})
   clj-ify))

(defn upsert [conn table x
              & {by-column :by
                 :or {by-column :id}}]
  {:pre [(some? table)
         (some? x)]}
  (let [value (get x by-column)]
    (jdbc/with-transaction [tx conn]
      (clj-ify
       (if (and value (fetch conn table value :by by-column))
         (execute-one! tx {:update table
                           :set (dissoc x :id)
                           :where [:= by-column value]})
         (execute-one! tx {:insert-into table
                           :values [x]}))))))

(defmethod ig/init-key :db/operations [_ {conn :connection}]
  {:fetch (partial fetch conn)
   :upsert (partial upsert conn)})
