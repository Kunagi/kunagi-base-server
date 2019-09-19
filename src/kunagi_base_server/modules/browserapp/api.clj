(ns kunagi-base-server.modules.browserapp.api
  (:require
   [html-tools.htmlgen :as htmlgen]

   [kunagi-base-server.modules.auth-server.auth :as auth]))


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
