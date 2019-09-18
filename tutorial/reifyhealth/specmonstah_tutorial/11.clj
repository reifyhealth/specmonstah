(ns reifyhealth.specmonstah-tutorial.11
  (:require [clojure.spec.alpha :as s]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]))

(s/def ::id (s/and pos-int? #(< % 100)))
(s/def ::topic-id ::id)
(s/def ::first-post-id ::id)

(s/def ::post (s/keys :req-un [::id ::topic-id]))
(s/def ::topic (s/keys :req-un [::id ::first-post-id]))

(def schema
  {:topic {:prefix    :t
           :spec      ::topic
           :relations {:first-post-id [:post :id]}}
   :post  {:prefix      :p
           :relations   {:topic-id [:topic :id]}
           :constraints {:topic-id #{:required}}
           :spec        ::post}})

(defn ex-01
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:post [[1]]}))

(defn ex-02
  []
  (sm/view (sm/add-ents {:schema schema} {:post [[1]]})))
