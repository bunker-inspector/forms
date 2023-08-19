(ns forms.client.app
  (:require [reagent.dom :as dom]
            [reagent.core :as r]
            [re-frame.core :as re]
            [re-frame.db :refer [app-db]]
            [forms.client.config :as config]
            [forms.client.http :as http]
            [clojure.string :as str]))

(defn fetch-me [& _]
  (http/req :method :get
            :uri "/user/me"
            :on-success #(re/dispatch [:me-response %])
            :on-failure #(re/dispatch [:me-response %])))

(re/reg-event-db
 :initialize
 (fn [db _]
   (if (empty? db) {:user nil} db)))

(re/reg-event-fx
 :google-identity-received
 (fn [_ [_ google-identity]]
   {:login (.-credential google-identity)}))

(re/reg-fx
 :login
 (fn [credential]
   (http/req :method :post
             :uri "/login"
             :args {:credential credential}
             :on-success fetch-me
             :on-failure #(prn "Login failed...again!"))))

(defn post-auth-code [code]
  (http/req :method :post
            :uri "/auth-code"
            :args {:code (.-code code)
                   :scope (.-scope code)}))

(re/reg-fx :fetch-me fetch-me)

(re/reg-event-fx
 :me-response
 (fn [{db :db} [_ {:keys [success status body]}]]
   (cond
     success {:db (assoc db :user body)
              :user-updated body}
     (= 401 status) {:not-logged-in nil}
     :else {:db db})))

(re/reg-global-interceptor re/debug)

(re/reg-fx
 :log
 (fn [data] (cljs.pprint/pprint data)))

(def ^:private required-scopes
  '("https://www.googleapis.com/auth/youtube"
    "https://www.googleapis.com/auth/yt-analytics.readonly"
    "https://www.googleapis.com/auth/yt-analytics-monetary.readonly"))

(re/reg-fx
 :user-updated
 (fn [{:keys [token]}]
   (when-not token
     (.requestCode
      (.initCodeClient (.. js/google -accounts -oauth2)
                       #js {"client_id" config/client-id
                            "scope" (str/join " " required-scopes)
                            "access_type" "offline"
                            "ux_mode" "popup"
                            "callback" post-auth-code})))))

(re/reg-fx
 :not-logged-in
 (fn [_]
   (.prompt (.. js/google -accounts -id))))

(defn load-google-user []
  (.initialize (.. js/google -accounts -id)
               #js {"client_id" config/client-id
                    "callback"  #(re/dispatch [:google-identity-received %])}))

(defn prepare-login-prompt! []
  (set! (.-onload js/window)
        (fn []
          (load-google-user)
          (fetch-me))))

(defn root []
  [:div
   [:div
    (if (= nil (:user @app-db))
      "Not logged in."
      [:div
       [:p (str "Logged in as " (-> @app-db :user :email))]
       [:img {:src (-> @app-db :user :picture-url)}]])]])

(defn app []
  (dom/render [root]
              (. js/document (getElementById "root"))))

(defn init []
  (re/dispatch-sync [:initialize])
  (prepare-login-prompt!)
  (app))

(comment
  (cljs.pprint/pprint @app-db))
