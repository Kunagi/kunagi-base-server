(ns kunagi-base-server.http-server
  (:require
   [clojure.spec.alpha :as s]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [ring.util.response :as ring-resp]
   [ring.util.mime-type :as ring-mime]
   [org.httpkit.server :as http-kit]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.http-kit :as sente-adapter]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]

   [kunagi-base.appmodel :as appmodel :refer [def-extension def-module]]
   [kunagi-base.events :as events]
   [kunagi-base.startup :refer [def-init-function]]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base.assets :as assets]

   [kunagi-base.context :as context]))


(s/def ::route-path string?)


;;; appmodel


(def-module
  {:module/id ::http-server})




(def-extension
  {:schema {:route/module {:db/type :db.type/ref}
            :routes-wrapper/module {:db/type :db.type/ref}}})



(defn def-route [route]
  (appmodel/register-entity
   :route
   route))


(defn def-routes-wrapper [routes-wrapper]
  (appmodel/register-entity
   :routes-wrapper
   routes-wrapper))


;;;


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
            (tap> [:err ::http-request-handlig-failed ex])
            {:status 500
             :body "Internal Server Error"}))))))


(defn GET
  [{:as handler
    :keys [path]}]
  (s/assert ::route-path path)
  (compojure/GET path [] (new-get-handler handler)))


(defn- serve-file [file context]
  (if (and file (.exists file))
    (let [filename (or (-> context :http/request :params :filename)
                       (.getName file))]
      (-> (ring-resp/file-response (.getPath file) {})
          (ring-resp/content-type (ring-mime/ext-mime-type filename))
          (ring-resp/header "Content-Disposition"
                            (str "attachment; filename=\""
                                 filename
                                 "\""))))
    (ring-resp/not-found)))


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


(defn- serve-query-data [context]
  (a-query-handler context
                   (fn [result]
                     (-> result
                         (dissoc :context)
                         pr-str
                         (ring-resp/response)
                         (ring-resp/content-type "text/edn")))))


(defn- serve-query-file [context]
  (a-query-handler context
                   (fn [result]
                     (let [results (get result :results)
                           file (first results)]
                       (serve-file file context)))))

;;; appmodel routes


;;; asset route


(defn- serve-asset [context]
  (if-let [edn (-> context :http/request :params :edn)]
    (let [path (read-string edn)]
      (try
        (let [asset (assets/asset-for-output path context)]
          (if (instance? java.io.File asset)
            (serve-file asset context)
            (-> asset
                pr-str
                (ring-resp/response)
                (ring-resp/content-type "text/edn"))))
        (catch Throwable ex
          (tap> [:dbg ::providing-asset-failed ex])
          {:status 400
           :body "Providing asset failed"})))
    {:status 400
     :body "Missing Parameter: [edn]"}))


(defn- routes-from-appmodel []
  (let [routes (appmodel/q!
                '[:find ?path ?serve-f ?req-perms
                  :where
                  [?r :route/path ?path]
                  [?r :route/serve-f ?serve-f]
                  [?r :route/req-perms ?req-perms]])]
    (map
     (fn [[path serve-f req-perms]]
       (GET
        {:path path
         :serve-f serve-f
         :req-perms req-perms}))
     routes)))


(defn- wrappers-from-appmodel [app-db]
  (let [wrappers (appmodel/q!
                  '[:find ?wrapper-f
                    :where
                    [?r :routes-wrapper/wrapper-f ?wrapper-f]])]
    (map
     (fn [[wrapper-f]]
       (wrapper-f app-db))
     wrappers)))


;;; sente

(defn determine-sente-user-id [request]
  (str
   (-> request :session :auth/user-id)
   "/"
   (-> request :client-id)))


(defn- on-connections-changed
  [old-val new-val]
  (let [old-ids (:any old-val)
        new-ids (:any new-val)
        connected-ids (remove old-ids new-ids)
        disconnected-ids (remove new-ids old-ids)]
    (doseq [client-id connected-ids]
      (tap> [:!!! ::connected client-id]))
    (doseq [client-id disconnected-ids]
      (tap> [:!!! ::disconnected client-id]))))


(defn- on-event-received [event context]
  (events/dispatch-event! event context))


(defn- respond-to-client [send-fn sente-user-id event]
  (send-fn sente-user-id [:kunagi-base/event event]))


(defn- on-data-received [data]
  ;; (tap> [:!!! ::data-received data])
  (when (= :kunagi-base/event (-> data :id))
    (on-event-received
     (-> data :event second)
     (-> data
         context/from-http-async-data
         auth/update-context
         (assoc :comm/response-f (partial
                                  respond-to-client
                                  (-> data :send-fn)
                                  (-> data :uid)))))))


(defn- create-socket
  []
  (let [socket (sente/make-channel-socket!
                (sente-adapter/get-sch-adapter)
                {:user-id-fn determine-sente-user-id})]
    (add-watch (:connected-uids socket)
               :connected-uids
               (fn [_ _ old-val new-val]
                 (when (not= old-val new-val)
                   (on-connections-changed old-val new-val))))
    (sente/start-server-chsk-router! (:ch-recv socket) #(on-data-received %))
    socket))


(defonce sente-socket (create-socket)) ;; TODO create on demand


;;; http server

(defn- create-default-routes
  []
  [(GET {:path "/api/asset"
         :serve-f serve-asset
         :req-perms [:base/read-assets]})
   (GET {:path "/api/query"
         :serve-f serve-query-data
         :req-perms [:cqrs/query]})
   (GET {:path "/api/query-file"
         :serve-f serve-query-file
         :req-perms [:cqrs/query]})

   (compojure/GET  "/chsk"  req ((:ajax-get-or-ws-handshake-fn sente-socket) req))
   (compojure/POST "/chsk"  req ((:ajax-post sente-socket) req))

   (compojure-route/files "/"        {:root "target/public"}) ;; TODO remove in prod
   (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])



;; (defn- routes-from-cqrs [context]
;;   (->> (cqrs/query-sync context [:http-server/routes])
;;        :results
;;        (map (fn [{:as route :keys [method]}]
;;               (case method
;;                 ;:post (POST route)
;;                 (GET route))))))


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
  [app-db]
  (let [port (or (-> app-db :appconfig/config :http-server/port)
                 3000)
        routes (-> []
                   (into (routes-from-appmodel))
                   (into (create-default-routes)))
        wrappers (-> []
                     (into (wrappers-from-appmodel app-db)))]
    ;;(tap> [:dbg ::routes routes])
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server (wrap-routes routes wrappers) {:port port})
    app-db))


(def-init-function
  {:init-function/id ::start
   :init-function/f start!})
