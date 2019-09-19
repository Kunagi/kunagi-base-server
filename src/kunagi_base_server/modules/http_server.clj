(ns kunagi-base-server.modules.http-server
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-extension]]

   [kunagi-base.modules.startup :refer [def-init-function]]
   [kunagi-base-server.modules.http-server.api :as impl]))


(def-module
  {:module/id ::http-server})


(def-extension
  {:schema {:route/module {:db/type :db.type/ref}
            :routes-wrapper/module {:db/type :db.type/ref}}})


(defn def-route [route]
  (utils/assert-entity
   route
   {:req {:route/module ::am/entity-ref}}
   (str "Invalid route " (-> route :route/id) "."))
  (am/register-entity
   :route
   route))


(defn def-routes-wrapper [routes-wrapper]
  (utils/assert-entity
   routes-wrapper
   {:req {:routes-wrapper/module ::am/entity-ref}}
   (str "Invalid routes-wrapper " (-> routes-wrapper :routes-wrapper/id) "."))
  (am/register-entity
   :routes-wrapper
   routes-wrapper))


(def-init-function
  {:init-function/id ::start
   :init-function/module [:module/ident :http-server]
   :init-function/f impl/start!})
