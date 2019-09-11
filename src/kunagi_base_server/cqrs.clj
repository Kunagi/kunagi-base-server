(ns kunagi-base-server.cqrs
  (:require
   [compojure.core :as compojure]
   [ring.util.response :as resp]

   [kunagi-base.context :as context]
   [kunagi-cqrs.api :as cqrs]))


(defn query-handler [req]
  (if-let [edn (-> req :params :edn)]
    (let [context (context/from-http-request req)
          query (read-string edn)]
      (try
        (-> (cqrs/query-sync context query)
            :results
            pr-str
            (resp/response)
            (resp/content-type "text/edn"))
        (catch Throwable ex
          (tap> [:dbg ::querying-failed ex])
          {:status 400
           :body "Querying failed"})))
    {:status 400
     :body "Missing Parameter: [edn]"}))


(defn routes []
  (-> []
      (conj (compojure/GET "/api/query" [] query-handler))))
