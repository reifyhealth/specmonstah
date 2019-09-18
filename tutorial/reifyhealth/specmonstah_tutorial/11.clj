(ns reifyhealth.specmonstah-tutorial.11
  (:require [clojure.spec.alpha :as s]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]))

(s/def ::id (s/and pos-int? #(< % 100)))

(s/def ::post (s/keys :req-un [::id]))

(s/def ::favorite-ids (s/coll-of ::id))
(s/def ::user (s/keys :req-un [::id ::favorite-ids]))

(def schema
  {:post {:prefix :p
          :spec   ::post}
   :user {:prefix      :u
          :spec        ::user
          :relations   {:favorite-ids [:post :id]}
          :constraints {:favorite-ids #{:coll}}}})

(defn ex-01
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:user [[1]]}))

(defn ex-02
  []
  (sg/ent-db-spec-gen-attr {:schema schema}
                           {:user [[1 {:refs {:favorite-ids 3}}]]}))

(defn ex-03
  []
  (sm/view (sm/add-ents {:schema schema}
                        {:user [[2 {:refs {:favorite-ids 3}}]]})))

(defn ex-04
  []
  (sm/view (sm/add-ents {:schema schema}
                        {:user [[1 {:refs {:favorite-ids [:my-p0 :my-p1]}}]
                                [1 {:refs {:favorite-ids [:my-p2 :my-p3]}}]]})))
