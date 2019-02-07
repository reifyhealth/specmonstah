(ns reifyhealth.specmonstah-tutorial.02
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}})
(defn ex-01
  []
  (sm/add-ents {:schema schema} {:todo-list [[2]]}))

