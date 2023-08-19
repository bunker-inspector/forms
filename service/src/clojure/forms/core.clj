(ns forms.core
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [aleph.http :as http]
   [compojure.core :as compojure :refer [GET POST OPTIONS]]
   [compojure.route :as route]
   [ring.util.response :refer [response status]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :as cookie]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.cors :refer [wrap-cors]]
   [cheshire.core :as json]
   [forms.clients.google :as google]
   [clojure.tools.logging :as log])
  (:import java.util.Base64
           java.time.Instant
           java.sql.Timestamp))

(defn base64-decode [blob]
  (-> (Base64/getDecoder) (.decode blob) String.))

(defn parse-credential [credential]
  (let [[header payload & _] (->>
                              (str/split credential #"\.")
                              (take 2)
                              (map base64-decode)
                              (map json/parse-string))]
    {:header header
     :payload payload}))

(defn- google-id-payload->user [credential]
  {:email (get credential "email")
   :given-name (get credential "given_name")
   :family-name (get credential "family_name")
   :full-name (get credential "name")
   :picture-url (get credential "picture")})

(defmethod ig/init-key :web.routes/login [_ {:keys [login-handler
                                                    auth-code-handler]}]
  (compojure/routes
   (OPTIONS "/login" _ {:status 200 :body "OK"})
   (POST "/login" _ login-handler)
   (POST "/auth-code" _ auth-code-handler)))

(defmethod ig/init-key :web.routes/user [_ {:keys [me-handler]}]
  (compojure/routes
   (GET "/user/me" _ me-handler)))

(defmethod ig/init-key :web.handlers/login [_ {{:keys [upsert]} :db-ops}]
  (fn [{{credential "credential"} :body}]
    (let [{payload :payload} (parse-credential credential)
          user (upsert :users (google-id-payload->user payload) :by :email)]
      (-> (response {:ok true})
          (assoc :session {:user user})))))

(defmethod ig/init-key :web.handlers/me [_ {{:keys [fetch]} :db-ops}]
  (fn [{{{:keys [id]} :user :as session} :session}]
    (if id
      (-> (fetch :users id)
          (assoc :token (some? (fetch :tokens id :by :user-id)))
          (dissoc :scopes)
          response
          (assoc :session session))
      (-> (response {:msg "Not logged in."})
          (status 401)))))

(defmethod ig/init-key :web.handlers/auth-code [_ {:keys [upsert]}]
  (fn [{{code "code"} :body
        {{user-id :id} :user :as session} :session}]
    (let [{body :body} (google/exchange-auth-code code)
          now (Instant/now)]
      (upsert :tokens {:access-token (:access_token body)
                       :refresh-token (:refresh_token body)
                       :scope (:scope body)
                       :token-type (:token_type body)
                       :user-id user-id
                       :expires-at (Timestamp/from
                                    (.plusSeconds now
                                                  (:expires_in body)))}))
    {:ok true}))

(def ^:private not-found
  (route/not-found {:status 404
                    :body "Not found!"
                    :headers {"Content-Type" "text/plain"}}))

(defmethod ig/init-key :web.routes/root [_ {routes :routes}]
  (apply compojure/routes (conj routes not-found)))

(defmethod ig/prep-key :web.security/session-key [_ session-key-str]
  (.getBytes session-key-str))

(defn- wrap-error-fallback [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (log/error e)
           (-> (response "Something went wrong.")
               (status 500))))))

(defmethod ig/init-key :web/app [_ {:keys [routes session-key]
                                    {:keys [access-control-allow-methods
                                            access-control-allow-origin]} :cors}]
  (-> routes
      wrap-params
      wrap-json-body
      wrap-json-response
      (wrap-cors :access-control-allow-methods access-control-allow-methods
                 :access-control-allow-origin access-control-allow-origin
                 :access-control-allow-credentials ["true"])
      (wrap-session {:cookie-name "forms-session"
                     :store (cookie/cookie-store {:key session-key})})
      wrap-error-fallback))

(defmethod ig/init-key :web/server [_ {:keys [app opts]}]
  (http/start-server app opts))

(defmethod ig/halt-key! :web/server [_ server]
  (.close server))
