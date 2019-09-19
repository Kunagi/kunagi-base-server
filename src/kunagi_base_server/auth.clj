(ns kunagi-base-server.auth
  (:require
   [compojure.core :as compojure]

   [facts-db.api :as db]

   [kunagi-base.event-sourcing.api :as es]
   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base-server.http-server :refer [def-route]]))


(defn complete-user-for-browserapp-by-oauth-userinfos [user context]
  (let [user-id (-> user :user/id)
        oauth-users (es/projection context
                                   [:auth/oauth-users "singleton"]
                                   :oauth-users)
        [service sub] (-> oauth-users :user->oauth (get user-id))
        userinfos (es/projection context
                                 [:auth/oauth-userinfos "singleton"]
                                 :oauth-userinfos)
        userinfo (-> userinfos :service->sub->userinfo (get service) (get sub))
        user (assoc user :user/oauth-userinfo userinfo)]
    user))


(defn user--for-browserapp [context]
  (when-let [user-id (-> context :auth/user-id)]
    (let [user-name user-id]
      (-> {:user/id user-id
           :user/perms (-> context :auth/user-perms)}
          (complete-user-for-browserapp-by-oauth-userinfos context)))))


(defn- serve-sign-out [context]
  {:session nil
   :status 303
   :headers {"Location" "/"}})


(def-module
  {:module/id ::server-auth})


(def-route
  {:route/id ::sign-out
   :route/module [:module/ident :server-auth]
   :route/path "/sign-out"
   :route/serve-f serve-sign-out
   :route/req-perms []})
