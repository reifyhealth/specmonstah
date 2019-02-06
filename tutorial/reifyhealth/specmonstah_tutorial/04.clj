(ns reifyhealth.specmonstah-tutorial.04
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
  (sm/gen-ent-graph {:schema schema} {:todo-list [[2 {:refs {:owner-id :my-own-sweet-user}}]
                                                  [1]]}))

(defn ex-02
  []
  (sm/gen-ent-graph {:schema schema} {:todo-list [[1]
                                                  [1 {:refs {:owner-id :hamburglar}}]]
                                      :todo      [[1]
                                                  [1 {:refs {:todo-list-id :tl1}}]]}))

(defn ex-03
  []
  (sm/gen-ent-graph {:schema schema} {:todo-list [[:tl0]
                                                  [:tl1 {:refs {:owner-id :hamburglar}}]]
                                      :todo      [[1 {:refs {:todo-list-id :tl0}}]
                                                  [1 {:refs {:todo-list-id :tl1}}]]}))
