(ns reifyhealth.specmonstah-tutorial.03
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}})
(defn ex-01
  []
  (sm/gen-ent-graph {:schema schema} {:todo-list [[2]]}))

(defn ex-02
  []
  (sm/gen-ent-graph {:schema schema} {:todo-list [[:my-todo-list]
                                                  [:my-todoodle-do-list]]}))

(defn ex-03
  []
  (sm/gen-ent-graph {:schema schema} {:todo-list [[1]
                                                  [:work]
                                                  [1]
                                                  [:cones-of-dunshire-club]]}))
