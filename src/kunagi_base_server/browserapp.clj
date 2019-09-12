(ns kunagi-base-server.browserapp
  (:require
   [compojure.core :as compojure]

   [html-tools.htmlgen :as htmlgen]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base.context :as context]))


(defn- browserapp-config [req context]
  (-> {}
      (assoc :auth/user (cqrs/query-sync-r1 context [:auth/user--for-browserapp]))))


(defn serve-app [context]
  (let [app-info (-> context :db :app-info)]
    (htmlgen/page-html
     (-> context :http/request)
     {:modules [:browserapp]
      :browserapp-config-f #(browserapp-config % context)
      :js-build-name (-> context :db :config :browserapp/js-build-name)
      :browserapp-name (-> app-info :app-name)
      :title (-> app-info :app-label)})))


(defn serve-redirect-to-app [context]
  {:status 301 :headers {"Location" "/ui/"}})

