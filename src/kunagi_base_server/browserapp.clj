(ns kunagi-base-server.browserapp
  (:require
   [compojure.core :as compojure]

   [html-tools.htmlgen :as htmlgen]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base.context :as context]
   [kunagi-base.appmodel :refer [def-module]]

   [kunagi-base-server.auth :as auth]
   [kunagi-base-server.http-server :refer [def-route]]))


(defn- browserapp-config [req context]
  (-> {}
      (assoc :auth/user (auth/user--for-browserapp context))
      (assoc :app/info (-> context :db :app/info))
      (assoc :browserapp/config (-> context :db :appconfig/config :browserapp/config))))


(defn serve-app [context]
  (let [app-info (-> context :db :app/info)]
    (htmlgen/page-html
     (-> context :http/request)
     {:modules [:browserapp]
      :browserapp-config-f #(browserapp-config % context)
      :js-build-name (-> context :db :appconfig/config :browserapp/js-build-name)
      :browserapp-name (-> app-info :app-name)
      :title (-> app-info :app-label)})))


(defn serve-redirect-to-app [context]
  {:status 301 :headers {"Location" "/ui/"}})


(def-module
  {:module/id ::browserapp})

(def-route
  {:route/id ::root-redirect
   :route/path "/"
   :route/serve-f serve-redirect-to-app
   :route/req-perms []})

(def-route
  {:route/id ::ui-redirect
   :route/path "/ui"
   :route/serve-f serve-redirect-to-app
   :route/req-perms []})

(def-route
  {:route/id ::app
   :route/path "/ui/**"
   :route/serve-f serve-app
   :route/req-perms []})
