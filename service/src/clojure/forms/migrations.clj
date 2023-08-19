(ns forms.migrations
  (:require [forms.config :as config]
            [integrant.core :as ig]
            [ragtime
             [repl :as repl]
             [jdbc :as jdbc]]))

(def config
  {:datastore (-> (config/resolve-config) :db/connection jdbc/sql-database)
   :migrations (jdbc/load-resources "migrations")})

(defn migrate [& _] (repl/migrate config))
(defn rollback [& _] (repl/rollback config 9999))
(defn reset [& _] (rollback) (migrate))
