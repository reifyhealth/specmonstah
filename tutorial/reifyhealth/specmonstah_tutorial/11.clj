(ns reifyhealth.specmonstah-tutorial.11
  (:require [reifyhealth.specmonstah.core :as sm]
            [clojure.spec.alpha :as s]))

(s/def ::id (s/and pos-int? #(< % 100)))

(s/def ::post (s/keys :req-un [::id]))

(s/def ::favorite-ids (s/coll-of ::id))
(s/def ::user (s/keys :req-un [::id ::favorite-ids]))

(def schema
  {:post {:prefix :p}
   :user {:prefix    :u
          :relations {:favorite-ids [:post :id]}}})
