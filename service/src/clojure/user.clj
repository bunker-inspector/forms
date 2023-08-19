(ns user
  (:require
   [integrant.repl :as ir :refer [clear go halt prep init reset reset-all]]
   [integrant.core :as ig]
   [forms.config :as config]))

(defonce debug-a (atom {}))

(defn tap-fn [xs] (apply swap! debug-a assoc xs))
(add-tap tap-fn)

(comment
  (ir/set-prep! #(ig/prep (config/resolve-config)))
  (reset)

  (-> (config/resolve-config)  ig/prep)
  @debug-a
  (ig/init-key :web/middleware { :security-opts { :key :web/security } } ))
