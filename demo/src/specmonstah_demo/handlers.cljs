(ns specmonstah-demo.handlers
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.paths :as p]
            [cljs.reader :as reader]
            [specmonstah-demo.examples.schemas :as schemas]))

(rf/reg-event-db :init
  [rf/trim-v]
  (fn [db]
    (-> db
        (assoc-in (p/full-path :form [:schema :buffer :schema]) schemas/todo-schema-txt)
        (assoc-in (p/full-path :form [:query :buffer :query]) "{:todo [[3]]}"))))

(rf/reg-event-db :submit-query
  [rf/trim-v]
  (fn [db]
    (assoc db :query (reader/read-string (get-in db (p/full-path :form [:query :buffer :query]))))))

(rf/reg-event-db :select-node
  [rf/trim-v]
  (fn [db [node]]
    (assoc db :selected-node (keyword node))))
