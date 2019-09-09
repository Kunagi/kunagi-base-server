(ns kunagi-base-server.browserapp
  (:require
   [compojure.core :as compojure]

   [html-tools.htmlgen :as htmlgen]))


(defn app-html-servlet
  [{:keys [app-name
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


(defn routes [browserapp-config]
  [(compojure/GET  "/"               [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui"             [] {:status 301 :headers {"Location" "/ui/"}})
   (compojure/GET  "/ui/**"          [] (app-html-servlet browserapp-config))])
