(ns kunagi-base-server.http-server
  (:require
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [ring.middleware.reload :as ring-reload]
   [ring.middleware.oauth2 :as ring-oauth]
   [org.httpkit.server :as http-kit]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]

   [html-tools.htmlgen :as htmlgen]))


(defn app-html-servlet [{:as config
                         :keys [app-name
                                app-label
                                js-build-name]}]
  (fn [request]
    (htmlgen/page-html
     {:js-build-name js-build-name
      :modules [:browserapp]
      :app-name app-name
      :title app-label})))


(defn create-default-routes [config]
  [(compojure/GET  "/"               [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui"             [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui/**"          [] (app-html-servlet config))
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
           oauth2-config
           app-routes]}]
  (let [port (or port 3000)
        routes (-> []
                   (into app-routes)
                   (into (create-default-routes config)))]
    (tap> [::start! {:port port}])
    (http-kit/run-server (wrap-routes routes oauth2-config) {:port port})))
