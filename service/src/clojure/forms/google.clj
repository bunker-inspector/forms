(ns forms.google
  (:require [forms.clients.google :as client]
            [integrant.core :as ig])
  (:import java.time.Instant
           java.sql.Timestamp))

(defn refresh [token upsert]
  (let [{:keys [user-id refresh-token]} @token
        now (Instant/now)
        {{:keys [access_token expires_in scope token_type]} :body}
        (client/refresh-access-token refresh-token)]
    (reset! token
            (upsert :tokens
                    {:access-token access_token
                     :scope scope
                     :token-type token_type
                     :user-id user-id
                     :expires-at (Timestamp/from (.plusSeconds now expires_in))}
                    :by :user-id))
    token))

(defmethod ig/init-key :service.google/get-token-fn [_ {:keys [fetch upsert]}]
  (fn [user-id]
    (let [token (atom (fetch :tokens user-id :by :user-id))]
      (fn [token-accepting-api-fn]
        (let [{:keys [status] :as result} (->> @token
                                               :access-token
                                               token-accepting-api-fn)]
          (cond
            (#{200 202} status) result
            (#{401 403} status) (-> token
                                    (refresh upsert)
                                    deref
                                    :access-token
                                    token-accepting-api-fn)
            :else          (throw (ex-info "Request failed." result))))))))
