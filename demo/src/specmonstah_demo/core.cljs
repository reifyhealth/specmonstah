(ns specmonstah-demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [integrant.core :as ig]
            
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.load-all-handler-ns]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.config :as stconfig]
            [specmonstah-demo.components.app :as app]
            [specmonstah-demo.handlers]
            [specmonstah-demo.subs]
            
            [goog.events]))


(defn ^:dev/after-load -main []
  (rf/dispatch-sync [::stcf/init-system stconfig/default-config])
  (rf/dispatch-sync [:init])
  (r/render [app/app] (stcu/el-by-id "app")))

(defonce initial-load (delay (-main)))
@initial-load

(defn ^:dev/before-load stop [_]
  (when-let [system (:sweet-tooth/system @rfdb/app-db)]
    (ig/halt! system)))
