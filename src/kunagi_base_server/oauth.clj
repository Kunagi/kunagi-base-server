(ns kunagi-base-server.oauth
  (:require
   [compojure.core :as compojure]
   [ring.middleware.oauth2 :as ring-oauth]

   [kunagi-base.auth.api :as auth]
   [kunagi-base.events :as events]
   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base.context :as context]
   [kunagi-base.event-sourcing.api :as es]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base.auth.users-db :as users-db]
   [kunagi-base-server.http-server :refer [def-route def-routes-wrapper]]

   [kunagi-base-server.oauth]))


(def-module
  {:module/id ::server-oauth
   :module/ident :server-oauth})


(defn- user-id-by-oauth-google
  [context {:keys [email]}]
  (when-let [users-db (-> context :db :auth/users-db)]
    (users-db/user-id-by-google-email users-db email)))


(defn- user-id-by-oauth
  [context auth-info]
  (case (-> auth-info :service)
    :google (user-id-by-oauth-google context auth-info)))


(defn- authenticate
  [context auth-info]
  (tap> [:dbg ::authenticate auth-info])
  (user-id-by-oauth context (-> auth-info :oauth)))


(defn create-base-config
  [config secrets provider-key provider-specific-config]
  (let [users-config (get-in config [:http-server/oauth provider-key])]
    (if (:enabled? users-config)
      (let [own-uri (get-in config [:http-server/uri])
            prefix (or own-uri "")
            secrets (get-in secrets [provider-key])]
        (if-not secrets
          nil
          (-> {:launch-uri       (str "/oauth/" (name provider-key))
               :redirect-uri     (str prefix "/oauth/" (name provider-key) "/callback")
               :landing-uri      (str "/oauth/completed")
               :basic-auth?      true}
              (merge provider-specific-config)
              (merge users-config)
              (merge secrets)))))))


(defn create-google-config [config secrets]
  (create-base-config
   config
   secrets
   :google
   {:authorize-uri    "https://accounts.google.com/o/oauth2/v2/auth"
    :access-token-uri "https://www.googleapis.com/oauth2/v4/token"
    :scopes           ["openid" "email" "profile"]}))


(defn create-ring-oauth2-config
  [config secrets]
  (let [google (create-google-config config secrets)]
    (cond-> {} google (assoc :google google))))


(defn- decode-jwt
  [token]
  (let [decoder (com.auth0.jwt.JWT/decode token)
        claims (.getClaims decoder)
        keys (.keySet claims)]
    (reduce
     (fn [ret key]
       (let [claim (.get claims key)]
         (if (.isNull claim)
           ret
           (assoc ret (keyword key) (or (.asString claim)
                                        (.asInt claim)
                                        (boolean (.asBoolean claim)))))))
     {}
     keys)))


(defn serve-oauth-completed
  [context]
  ;; (tap> [:!!! :oauth (-> context :http/request)])
  (let [request (-> context :http/request)
        access-tokens (-> request :session :ring.middleware.oauth2/access-tokens)
        service (-> access-tokens keys first)
        tokens-map (get access-tokens service)
        ;; token (:token tokens-map)
        id-token (:id-token tokens-map)
        userinfo (decode-jwt id-token)]

    ;; TODO use a command
    (es/aggregate-events
     [:auth/oauth-userinfos "sigleton"]
     [
      [:oauth-userinfo-received
       {:service service
        :userinfo userinfo}]])

    (let [!user-id (promise)]
      (events/dispatch-event!
       [:kunagi-base/command-triggered
        [:auth/oauth-users "singleton"]
        [:auth/sign-in-with-oauth {:service service
                                   :userinfo userinfo
                                   :user-id-promise !user-id}]]
       (auth/authorize-context context)))
      ;; TODO wait for promise

    (let [user-id (authenticate
                   (context/from-http-request request)
                   {:oauth {:service service
                            :sub (:sub userinfo)
                            :email (:email userinfo)
                            :name (:name userinfo)
                            :picture (:picture userinfo)}})]
      (if user-id
        (tap> [:inf ::authenticated user-id])
        (tap> [:inf ::authentication-failed userinfo]))
      {:session {:auth/user-id user-id}
       :status 303
       :headers {"Location" "/"}})))


(def-route
  {:route/id ::oauth-completed
   :route/path "/oauth/completed"
   :route/serve-f serve-oauth-completed
   :route/req-perms []})


(defn- oauth2-wrapper [app-db]
  (fn [routes]
    (let [config (-> app-db :appconfig/config)
          secrets (-> app-db :appconfig/secrets-f (apply []) :oauth)]
      (ring-oauth/wrap-oauth2 routes (create-ring-oauth2-config config secrets)))))


(def-routes-wrapper
  {:routes-wrapper/id ::oauth2
   :routes-wrapper/wrapper-f oauth2-wrapper})


(defn wrappers []
  [(fn [routes]
     (oauth2-wrapper))])
