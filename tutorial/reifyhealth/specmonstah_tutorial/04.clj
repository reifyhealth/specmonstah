(ns reifyhealth.specmonstah-tutorial.04
  (:require [reifyhealth.specmonstah.core :as sm]))

(def schema
  {:user  {:prefix :u}
   :topic {:prefix    :t
           :relations {:owner-id [:user :id]}}
   :post  {:prefix    :p
           :relations {:topic-id [:topic :id]
                       :owner-id [:user :id]}}})
(defn ex-01
  []
  (sm/add-ents {:schema schema} {:topic [[2 {:refs {:owner-id :my-own-sweet-user}}]
                                         [1]]}))

(sm/view (ex-02))
(defn ex-02
  []
  (sm/add-ents {:schema schema} {:topic [[1]
                                         [1 {:refs {:owner-id :hamburglar}}]]
                                 :post  [[1]
                                         [1 {:refs {:topic-id :t1}}]]}))

(defn ex-03
  []
  (sm/add-ents {:schema schema} {:topic [[:t0]
                                         [:t1 {:refs {:owner-id :hamburglar}}]]
                                 :post  [[1 {:refs {:topic :tl0}}]
                                         [1 {:refs {:topic :tl1}}]]}))
