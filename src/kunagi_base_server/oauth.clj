(ns kunagi-base-server.oauth
  (:require
   [compojure.core :as compojure]
   [ring.middleware.oauth2 :as ring-oauth]

   [kunagi-base.context :as context]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base.auth.users-db :as users-db]))


(defn- user-id-by-oauth-google
  [context {:keys [email]}]
  (cqrs/query-sync-r1 context [:auth/user-id-by-google-email email]))


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
  (let [users-config (get-in config [provider-key])]
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


(defn server-oauth-completed
  [request]
  (let [access-tokens (-> request :session :ring.middleware.oauth2/access-tokens)
        google (:google access-tokens)
        access-token (:token google)
        id-token (:id-token google)
        userinfo (decode-jwt id-token)]
    (let [user-id (authenticate
                   (context/from-http-request request)
                   {:oauth {:service :google
                            :sub (:sub userinfo)
                            :email (:email userinfo)
                            :name (:name userinfo)}})]
      (if user-id
        (tap> [:inf ::authenticated user-id])
        (tap> [:inf ::authentication-failed userinfo]))
      {:session {:auth/user-id user-id}
       :status 303
       :headers {"Location" "/"}})))


(defn routes []
  [(compojure/GET "/oauth/completed" [] server-oauth-completed)])


(defn wrappers [{:keys [config
                        secrets]}]
  [(fn [routes]
     (ring-oauth/wrap-oauth2 routes (create-ring-oauth2-config config secrets)))])
