(ns kunagi-base-server.auth
  (:require
   [compojure.core :as compojure]

   [facts-db.api :as db]

   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base-server.http-server :refer [def-route]]))


(defn user--for-browserapp [context]
  (when-let [users-db (-> context :db :auth/users-db)]
    (when-let [user (db/query users-db [:user--for-browserapp (-> context :auth/user-id)])]
      (-> user
          (assoc :user/perms (-> context :auth/user-perms))))))


(defn- serve-sign-out [context]
  {:session nil
   :status 303
   :headers {"Location" "/"}})


(def-module
  {:module/id ::server-auth})


(def-route
  {:route/id ::sign-out
   :route/path "/sign-out"
   :route/serve-f serve-sign-out
   :route/req-perms []})
