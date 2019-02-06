(ns reifyhealth.specmonstah-tutorial.05
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}
   :todo      {:prefix :t
               :relations {:todo-list-id [:todo-list :id]}}})

(defn ex-01
  []
  (let [ent-db-1 (sm/gen-ent-graph {:schema schema} {:todo-list [[1]]})
        ent-db-2 (sm/gen-ent-graph ent-db-1 {:todo-list [[1] [1 {:refs {:owner-id :hamburglar}}]]})]
    (lio/view (:data ent-db-1))
    (lio/view (:data ent-db-2))))
