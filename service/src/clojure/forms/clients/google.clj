(ns forms.clients.google
  (:require [forms.config :as config]
            [clj-http.client :as client]))

;; POST /token HTTP/1.1
;; Host: oauth2.googleapis.com
;; Content-Type: application/x-www-form-urlencoded
;;
;; code=4/P7q7W91a-oMsCeLvIaQm6bTrgtp7&
;; client_id=your_client_id&
;; client_secret=your_client_secret&
;; redirect_uri=https%3A//oauth2.example.com/code&
;; grant_type=authorization_code

(def ^:private google-api-endpoint
  "https://oauth2.googleapis.com")

(def ^:private oauth-config
  (select-keys (config/resolve-config)
               [:oauth/client-id
                :oauth/redirect-uri
                :oauth/client-secret]))

(defn exchange-auth-code [auth-code]
  (client/post (str google-api-endpoint "/token")
               {:content-type "application/x-www-form-urlencoded"
                :form-params {:code auth-code
                              :client_id (:oauth/client-id oauth-config)
                              :client_secret (:oauth/client-secret oauth-config)
                              :redirect_uri (:oauth/redirect-uri oauth-config)
                              :grant_type "authorization_code"}
                :as :json}))

(defn refresh-access-token [refresh-token]
  (client/post (str google-api-endpoint "/token")
               {:content-type "application/x-www-form-urlencoded"
                :form-params {:client_id (:oauth/client-id oauth-config)
                              :client_secret (:oauth/client-secret oauth-config)
                              :refresh_token refresh-token
                              :grant_type "refresh_token"}
                :as :json}))

(defn revoke-token [access-token]
  (client/post (str google-api-endpoint "/revoke")
               {:content-type "application/x-www-form-urlencoded"
                :query-params {"token" access-token}
                :as :json}))
