(ns reifyhealth.specmonstah-tutorial.08
  (:require [reifyhealth.specmonstah.core :as sm]))


(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}
   :todo      {:prefix    :t
               :relations {:todo-list-id [:todo-list :id]}}})

(defn announce
  [db ent-name visit-key]
  (str "announcing... " ent-name "!"))

(defn ex-01
  []
  (-> (sm/build-ent-db {:schema schema} {:todo [[1]]})
      (sm/visit-ents :announce announce)
      (get-in [:data :attrs])))

(ex-01)
