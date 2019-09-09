(ns kunagi-base-server.http-server
  (:require
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [ring.middleware.reload :as ring-reload]
   [ring.middleware.oauth2 :as ring-oauth]
   [org.httpkit.server :as http-kit]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]

   [html-tools.htmlgen :as htmlgen]
   [kunagi-base.appconfig.api :as config]

   [kunagi-base-server.oauth :as oauth]))


(defn app-html-servlet [{:as config
                         :keys [app-name
                                app-label
                                js-build-name
                                browserapp-config-f]}]
  (fn [request]
    (htmlgen/page-html
     request
     {:js-build-name js-build-name
      :modules [:browserapp]
      :browserapp-name app-name
      :browserapp-config-f browserapp-config-f
      :title app-label})))


(defn create-default-routes
  [{:as config
    :keys [authenticate-f]}]
  [(compojure/GET  "/"               [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui"             [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui/**"          [] (app-html-servlet config))
   (compojure/GET "/oauth/completed" [] (fn [req] (oauth/handle-oauth-completed req authenticate-f)))
   (compojure-route/files "/"        {:root "target/public"}) ;; TODO remove in prod
   (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])

(defn- wrap-routes [routes oauth2-config]

  (-> compojure/routes
      (apply routes)

      (ring-oauth/wrap-oauth2 oauth2-config)

      (ring-params/wrap-params)

      (ring-defaults/wrap-defaults
       (-> ring-defaults/site-defaults
           (assoc-in [:session :cookie-attrs :same-site] :lax)))))


(defn start!
  [{:as config
    :keys [port
           app-routes]}]
  (let [port (or port 3000)
        routes (-> []
                   (into app-routes)
                   (into (create-default-routes config)))
        oauth2-config (oauth/create-ring-oauth2-config config (config/secrets))]
    (tap> [:dbg ::oauth2-config oauth2-config])
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server (wrap-routes routes oauth2-config) {:port port})))
