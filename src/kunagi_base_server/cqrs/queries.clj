(ns kunagi-base-server.cqrs.queries
  (:require
   [kunagi-base.cqrs.queries]
   [kunagi-base.cqrs.api :as cqrs :refer [def-query-responder]]

   [kunagi-base-server.browserapp :as browserapp]))


;;; http server routes

(def-query-responder
  :http-server/routes
  ::ident
  (fn [_ _]
    [{:path "/"
      :serve-f browserapp/serve-redirect-to-app
      :req-perms []}
     {:path "/ui"
      :serve-f browserapp/serve-redirect-to-app
      :req-perms []}
     {:path "/ui/**"
      :serve-f browserapp/serve-app
      :req-perms []}]))
