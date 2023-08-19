(ns user
  (:require
   [integrant.repl :as ir :refer [clear go halt prep init reset reset-all]]
   [integrant.core :as ig]
   [forms.config :as config]))

(comment
  (do
    (ir/set-prep! #(ig/prep (config/resolve-config)))

    (defonce debug-a (atom {}))
    (defn tap-fn [xs] (apply swap! debug-a assoc xs))
    (add-tap tap-fn)))

(comment
  (reset))
