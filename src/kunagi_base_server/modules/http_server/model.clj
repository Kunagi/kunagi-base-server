(ns kunagi-base-server.modules.http-server.model
  (:require
   [clojure.spec.alpha :as s]

   [kcu.sapp :as sapp]

   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]

   [kunagi-base.modules.startup.model :refer [def-init-function]]
   [kunagi-base-server.modules.http-server.api :as impl]))


(def-module
  {:module/id ::http-server})


;;; route


(def-entity-model
  :http-server ::route
  {:route/path {:uid? true :spec string?}
   :route/ident {} ; deprecated
   :route/serve-f {:req? true :spec fn?}
   :route/req-perms {:spec (s/coll-of qualified-keyword?)}})


(defn def-route [route]
  (let [route (assoc route :route/method (get route :route/method :get))]
    (utils/assert-entity
     route
     {:req {:route/module ::am/entity-ref}}
     (str "Invalid route " (-> route :route/id) "."))
    (am/register-entity
     :route
     route)))


;;; routes-wrapper


(def-entity-model
  :http-server ::routes-wrapper
  {:routes-wrapper/wrapper-f {:req? true :spec fn?}
   :routes-wrapper/ident {}}) ; deprecated


(defn def-routes-wrapper [routes-wrapper]
  (utils/assert-entity
   routes-wrapper
   {:req {:routes-wrapper/module ::am/entity-ref}}
   (str "Invalid routes-wrapper " (-> routes-wrapper :routes-wrapper/id) "."))
  (am/register-entity
   :routes-wrapper
   routes-wrapper))


;;;


(def-init-function
  {:init-function/id ::start
   :init-function/module [:module/ident :http-server]
   :init-function/f impl/start!})


(def-route
  {:route/id ::anti-forgery-token
   :route/module [:module/ident :http.server]
   :route/path "/api/anti-forgery-token"
   :route/serve-f #(-> % :http/request
                       :session
                       :ring.middleware.anti-forgery/anti-forgery-token)
   :route/req-perms []})


(def-route
  {:route/id ::conversation
   :route/module [:module/ident :demo-serverapp]
   :route/path "/api/conversation"
   :route/method :post
   :route/serve-f #(sapp/serve-conversation %)
   :route/req-perms []})
