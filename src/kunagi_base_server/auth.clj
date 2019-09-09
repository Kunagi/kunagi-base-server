(ns kunagi-base-server.auth
  (:require
   [compojure.core :as compojure]))


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


(defn routes []
  [(compojure/GET "/sign-out" [] signout-handler)])
