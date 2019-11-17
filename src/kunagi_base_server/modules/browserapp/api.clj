(ns kunagi-base-server.modules.browserapp.api
  (:require
   [html-tools.htmlgen :as htmlgen]

   [kunagi-base-server.modules.auth-server.auth :as auth]))


(defn- browserapp-config [req context]
  (-> {}
      (merge (-> context :db :appconfig/config :browserapp/config))
      (assoc :auth/user (auth/user--for-browserapp context))
      (assoc :app/info (-> context :db :app/info))))


(defn serve-app [context]
  (let [v (-> context :http/request :params (get "v"))
        app-info (-> context :db :app/info)
        config (-> context :db :appconfig/config)
        lang (or (-> config :browserapp/lang) "en")
        ;; google-analytics-tracking-id (-> config :google-analytics/tracking-id)
        cookie-consent-script-url (-> config :browserapp/cookie-consent-script-url)
        head-contents []
        head-contents (if cookie-consent-script-url
                        (conj head-contents [:script {:src cookie-consent-script-url}])
                        head-contents)
        favicon? (if (contains? config :browserapp/favicon?)
                   (-> config :browserapp/favicon?)
                   true)
        head-contents (if favicon?
                        (conj head-contents [:link {:rel "icon"
                                                    :type "image/png"
                                                    :href "/favicon.png"}])
                        head-contents)
        error-alert? (-> config :browserapp/error-alert?)
        modules (cond-> [:browserapp :manifest-json]
                  error-alert? (conj :error-alert))]

    (htmlgen/page-html
     (-> context :http/request)
     {:modules modules
      :lang lang
      :head-contents head-contents
      :browserapp-config-f #(browserapp-config % context)
      :js-build-name (-> context :db :appconfig/config :browserapp/js-build-name)
      :js-build-v v
      :browserapp-name (-> app-info :app-name)
      :title (-> app-info :app-label)})))
      ;;:google-analytics-tracking-id google-analytics-tracking-id})))


(defn serve-redirect-to-app [context]
  {:status 301 :headers {"Location" "/ui/"}})
