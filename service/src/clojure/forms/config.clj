(ns forms.config
  (:require [integrant.core :as ig]
            [aero.core :as aero]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defmacro integrant-passthrough [key]
  `(defmethod ig/init-key ~key [~'_ ~'value] ~'value))

(defmethod aero/reader 'ig/ref [_ _ value] (integrant.core/ref value))

(defmethod aero/reader 'regex [_ _ value] (re-pattern value))

(defmethod aero/reader 'const [_ _ key] (do (integrant-passthrough key)
                                            key))

(defmethod aero/reader 'file [{source :source} _ filename]
  (let [source-dir (or (.getParent (java.io.File. source))
                       (System/getProperty "user.dir"))]
    (aero/read-config (str source-dir "/" filename))))

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn resolve-config []
  (->> (str/split (System/getProperty "config.extras") #",")
       (cons "config.edn")
       (filter #(-> % io/file .exists))
       (map aero/read-config)
       (apply deep-merge)))
