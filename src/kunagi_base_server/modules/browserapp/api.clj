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
  (let [app-info (-> context :db :app/info)
        config (-> context :db :appconfig/config)
        google-analytics-tracking-id (-> config :google-analytics/tracking-id)
        cookie-consent-script-url (-> config :browserapp/cookie-consent-script-url)
        head-contents []
        head-contents (if cookie-consent-script-url
                        (conj head-contents [:script {:src cookie-consent-script-url}])
                        head-contents)]
    (htmlgen/page-html
     (-> context :http/request)
     {:modules (cond->
                   [:browserapp :manifest-json]

                 google-analytics-tracking-id
                 (conj :google-analytics))
      :head-contents head-contents
      :browserapp-config-f #(browserapp-config % context)
      :js-build-name (-> context :db :appconfig/config :browserapp/js-build-name)
      :browserapp-name (-> app-info :app-name)
      :title (-> app-info :app-label)
      :google-analytics-tracking-id google-analytics-tracking-id})))


(defn serve-redirect-to-app [context]
  {:status 301 :headers {"Location" "/ui/"}})
