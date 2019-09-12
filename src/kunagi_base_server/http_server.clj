(ns kunagi-base-server.http-server
  (:require
   [clojure.spec.alpha :as s]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [ring.util.response :as ring-resp]
   [ring.util.mime-type :as ring-mime]
   [org.httpkit.server :as http-kit]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]

   [kunagi-base.auth.api :as auth]
   [kunagi-base.cqrs.api :as cqrs]

   [kunagi-base.context :as context]))


(s/def ::route-path string?)


(defn- request-permitted?
  [context {:as handler
            :keys [req-perms]}]
  (if (nil? req-perms)
    false
    (auth/context-has-permissions? context req-perms)))


(defn- new-get-handler
  [{:as handler
    :keys [serve-f]}]
  (fn [req]
    (let [context (-> (context/from-http-request req)
                      (auth/update-context))]
      (if-not (request-permitted? context handler)
        {:status 403
         :body "Forbidden"}
        (try
          (serve-f context)
          (catch Throwable ex
            (tap> [:wrn ::http-request-handlig-failed req ex])
            {:status 500
             :body "Internal Server Error"}))))))


(defn GET
  [{:as handler
    :keys [path]}]
  (s/assert ::route-path path)
  (compojure/GET path [] (new-get-handler handler)))


;;; cqrs routes


(defn- a-query-handler [context query-result>http-response]
  (if-let [edn (-> context :http/request :params :edn)]
    (let [query (read-string edn)]
      (try
        (let [result (cqrs/query-sync context query)]
          (query-result>http-response result))
        (catch Throwable ex
          (tap> [:dbg ::querying-failed ex])
          {:status 400
           :body "Querying failed"})))
    {:status 400
     :body "Missing Parameter: [edn]"}))


(defn- query-data-handler [context]
  (a-query-handler context
                   (fn [result]
                     (-> result
                         (dissoc :context)
                         pr-str
                         (ring-resp/response)
                         (ring-resp/content-type "text/edn")))))


(defn- query-file-handler [context]
  (a-query-handler context
                   (fn [result]
                     (let [results (get result :results)
                           file (first results)]
                       (if (and file (.exists file))
                         (let [filename (or (-> context :http/request :params :filename)
                                            (.getName file))]
                           (-> (ring-resp/file-response (.getPath file) {})
                               (ring-resp/content-type (ring-mime/ext-mime-type filename))
                               (ring-resp/header "Content-Disposition"
                                            (str "attachment; filename=\""
                                                 filename
                                                 "\""))))
                         (ring-resp/not-found))))))


;;; http server

(defn- create-default-routes
  []
  [(GET {:path "/api/query"
         :serve-f query-data-handler
         :req-perms [:cqrs/query]})
   (GET {:path "/api/query-file"
         :serve-f query-file-handler
         :req-perms [:cqrs/query]})
   (compojure-route/files "/"        {:root "target/public"}) ;; TODO remove in prod
   (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])



(defn- routes-from-cqrs [context]
  (->> (cqrs/query-sync context [:http-server/routes])
       :results
       (map (fn [{:as route :keys [method]}]
              (case method
                ;:post (POST route)
                (GET route))))))


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
  [context
   {:keys [port
           app-routes
           app-wrappers]}]
  (let [port (or port 3000)
        routes (-> []
                   (into (routes-from-cqrs context))
                   (into app-routes)
                   (into (create-default-routes)))
        wrappers (-> []
                     (into app-wrappers))]
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server (wrap-routes routes wrappers) {:port port})))



