(ns forms.core
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [aleph.http :as http]
   [reitit.ring :as ring]
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

(defn- wrap-error-fallback [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (log/error e)
           (-> (response "Something went wrong.")
               (status 500))))))

(defmethod ig/init-key :web/middleware [_ {{:keys [access-control-allow-origin
                                                   access-control-allow-methods]} :cors
                                           session-key :session-key}]
  [wrap-params
   wrap-json-body
   wrap-json-response
   #(wrap-cors % :access-control-allow-methods access-control-allow-methods
               :access-control-allow-origin access-control-allow-origin
               :access-control-allow-credentials ["true"])
   #(wrap-session % {:cookie-name "forms-session"
                     :store (cookie/cookie-store {:key session-key})})
   wrap-error-fallback])

(defmethod ig/init-key :web/router [_ {:keys [routes middleware]}]
  (ring/router
   routes
   {:data {:middleware middleware}}))

(defmethod ig/init-key :web/routes [_ {handlers :handlers}]
  [["/login"
    {:get {}}
    {:options {:handler (constantly {:status 200 :body "OK"})}}]
   ["/user/me"
    {:get {:handler (:me handlers)}}]
   ["/auth-code"
    {:get {:handler (:auth-code handlers)}}]])

(defn me-handler [& {:keys [fetch]}]
  (fn [{{{:keys [id]} :user :as session} :session}]
    (if id
      (-> (fetch :users id)
          (assoc :token (some? (fetch :tokens id :by :user-id)))
          (dissoc :scopes)
          response
          (assoc :session session))
      (-> (response {:msg "Not logged in."})
          (status 401)))))

(defn auth-code-handler [& {:keys [upsert]}]
  (fn [{{code "code"} :body
        {{user-id :id} :user} :session}]
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

(defmethod ig/init-key :web/handlers [_ {{:keys [fetch upsert]} :db-ops}]
  {:me (me-handler :fetch fetch)
   :auth-code (auth-code-handler :upsert upsert)})

#_(defmethod ig/init-key :web.handlers/login [_ {{:keys [upsert]} :db-ops}]
  (fn [{{credential "credential"} :body}]
    (let [{payload :payload} (parse-credential credential)
          user (upsert :users (google-id-payload->user payload) :by :email)]
      (-> (response {:ok true})
          (assoc :session {:user user})))))

(defmethod ig/prep-key :web/security [_ opts]
  (update opts :session-key #(.getBytes %)))

(defmethod ig/init-key :web/security [_ x] x)

(defmethod ig/init-key :web/app [_ {:keys [router]}]
  (ring/ring-handler router (ring/create-default-handler)))

(defmethod ig/init-key :web/server [_ {:keys [app opts]}]
  (http/start-server app opts))

(defmethod ig/halt-key! :web/server [_ server]
  (.close server))
