(ns reifyhealth.specmonstah-tutorial.03
  (:require [reifyhealth.specmonstah.core :as sm]))

(def schema
  {:user {:prefix :u}
   :post {:prefix    :p
          :relations {:owner-id [:user :id]}}})

(defn ex-01
  []
  (sm/add-ents {:schema schema} {:post [[2]]}))

(defn ex-02
  []
  (sm/add-ents {:schema schema} {:post [[:my-post]
                                        [:blorp-post]]}))

(defn ex-03
  []
  (sm/add-ents {:schema schema} {:post [[1]
                                        [:work]
                                        [1]
                                        [:cones-of-dunshire-club]]}))
