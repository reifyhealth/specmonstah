(ns specmonstah-demo.handlers
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.paths :as p]
            [specmonstah-demo.examples.schemas :as schemas]))

(rf/reg-event-db :init
  [rf/trim-v]
  (fn [db]
    (assoc-in db (p/full-path :form [:schema :buffer :schema]) schemas/todo-schema-txt)))
