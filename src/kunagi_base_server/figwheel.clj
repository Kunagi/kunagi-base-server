(ns kunagi-base-server.figwheel)


(defn ring-handler-for-figwheel [start-fn]
  (fn [request]
    (let [href (str "http://localhost:" 3000)]
      (if (and (= :get (:request-method request))
               (= "/"  (:uri request)))
        (do
          (start-fn)
          {:status 302
           :headers {"Location" href}})
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body (str "404 - Page not found\n"
                    "\n"
                    "This is the ring handler for figwheel.\n"
                    "Goto " href)}))))
