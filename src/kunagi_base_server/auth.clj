(ns kunagi-base-server.auth
  (:require
   [compojure.core :as compojure]

   [kunagi-base-server.oauth :as oauth]))


(defn handler-with-auth
  ([handler]
   (handler-with-auth handler :authenticated))
  ([handler required-permission]
   (fn [req]
     (if-let [user-id (-> req :session :auth/user-id)]
       (handler req)
       {:status 401
        :body "Unauthorized"}))))


(defn signout-handler [request]
  {:session nil
   :status 303
   :headers {"Location" "/"}})


(defn routes [config]
  (-> []
      (conj (compojure/GET "/sign-out" [] signout-handler))
      (into (oauth/routes config))))


(defn wrappers [config]
  (-> []
      (into (oauth/wrappers config))))
