(ns kunagi-base-server.modules.auth-server
  (:require
   [kunagi-base.appmodel :refer [def-module def-extension]]

   [kunagi-base-server.modules.http-server :refer [def-route def-routes-wrapper]]
   [kunagi-base-server.modules.auth-server.auth :as auth]
   [kunagi-base-server.modules.auth-server.oauth2 :as oauth2]))


(def-module
  {:module/id ::auth-server})


(def-route
  {:route/id ::sign-out
   :route/module [:module/ident :auth-server]
   :route/path "/sign-out"
   :route/serve-f auth/serve-sign-out
   :route/req-perms []})

(def-route
  {:route/id ::oauth-completed
   :route/module [:module/ident :auth-server]
   :module/ident :server-oauth
   :route/path "/oauth/completed"
   :route/serve-f oauth2/serve-oauth-completed
   :route/req-perms []})

(def-routes-wrapper
  {:routes-wrapper/id ::oauth2
   :routes-wrapper/module [:module/ident :auth-server]
   :routes-wrapper/wrapper-f oauth2/oauth2-wrapper})
