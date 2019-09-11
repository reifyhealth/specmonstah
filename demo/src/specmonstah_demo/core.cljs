(ns specmonstah-demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [integrant.core :as ig]
            
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.load-all-handler-ns]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.config :as stconfig]
            [sweet-tooth.frontend.handlers :as sthandlers]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.js-event-handlers.flow :as stjehf]

            [specmonstah-demo.components.app :as app]
            [specmonstah-demo.handlers]
            [specmonstah-demo.subs]
            
            [goog.events]))


(defn system-config
  "This is a function instead of a static value so that it will pick up
  reloaded changes"
  []
  (merge stconfig/default-config
         {::stfr/frontend-router {:use    :reitit
                                  :routes {}}
          ::stjehf/handlers      {}}))

(defn ^:dev/after-load -main []
  (rf/dispatch-sync [::stcf/init-system (system-config)])
  (rf/dispatch-sync [:init])
  (r/render [app/app] (stcu/el-by-id "app")))

(defonce initial-load (delay (-main)))
@initial-load

(defn ^:dev/before-load stop [_]
  (when-let [system (:sweet-tooth/system @rfdb/app-db)]
    (ig/halt! system)))
