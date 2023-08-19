(ns forms.client.http
  (:require [forms.client.config :as config]
            [cljs-http.client :as cljs-http]
            [clojure.core.async :refer [go <!]]))

(defn req
  [& {:keys [method uri args on-success on-failure]
      :or {on-success identity
           on-failure identity}}]
  (go (let [http-fn (case method
                      :get cljs-http/get
                      :post cljs-http/post)
            request-params (if (empty? args) [] [{:json-params args}])
            {:keys [success] :as response} (<! (apply http-fn
                                                      (str config/server-url uri)
                                                      request-params))]
        (if success (on-success response) (on-failure response)))))
