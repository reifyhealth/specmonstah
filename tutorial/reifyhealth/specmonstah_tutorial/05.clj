(ns reifyhealth.specmonstah-tutorial.05
  (:require [reifyhealth.specmonstah.core :as sm]))

(def schema
  {:user  {:prefix :u}
   :topic {:prefix    :t
           :relations {:owner-id [:user :id]}}})

(defn ex-01
  []
  (let [ent-db-1 (sm/add-ents {:schema schema} {:topic [[1]]})
        ent-db-2 (sm/add-ents ent-db-1 {:topic [[1]
                                                [1 {:refs {:owner-id :hamburglar}}]]})]
    (sm/view ent-db-1)
    (sm/view ent-db-2)))

(ex-01)


(sm/add-ents ent-db-1 {:topic [[1]
                               [1 {:refs {:owner-id :hamburglar}}]]})
