(ns kunagi-base-server.http-server
  (:require
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [org.httpkit.server :as http-kit]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]))


(defn create-default-routes
  []
  [(compojure-route/files "/"        {:root "target/public"}) ;; TODO remove in prod
   (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])

(defn- apply-wrappers [routes wrappers]
  (reduce
   (fn [routes wrapper]
     (wrapper routes))
   routes
   wrappers))

(defn- wrap-routes [routes wrappers]

  (-> compojure/routes
      (apply routes)

      (apply-wrappers wrappers)

      (ring-params/wrap-params)

      (ring-defaults/wrap-defaults
       (-> ring-defaults/site-defaults
           (assoc-in [:session :cookie-attrs :same-site] :lax)))))


(defn start!
  [{:keys [port
           app-routes
           app-wrappers]}]
  (let [port (or port 3000)
        routes (-> []
                   (into app-routes)
                   (into (create-default-routes)))
        wrappers (-> []
                     (into app-wrappers))]
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server (wrap-routes routes wrappers) {:port port})))
