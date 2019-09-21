(ns reifyhealth.specmonstah-tutorial.06
  (:require [reifyhealth.specmonstah.core :as sm]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [reifyhealth.specmonstah.spec-gen :as sg]))

(s/def ::id (s/and pos-int? #(< % 100)))
(s/def ::not-empty-string (s/and string? not-empty #(< (count %) 10)))

(s/def ::username ::not-empty-string)
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::name ::not-empty-string)
(s/def ::topic (s/keys :req-un [::id ::name ::owner-id]))

(s/def ::owner-id ::id)
(s/def ::topic-id ::id)
(s/def ::content ::not-empty-string)
(s/def ::post (s/keys :req-un [::id ::owner-id ::topic-id ::content]))

(def schema
  {:user  {:prefix :u
           :spec   ::user}
   :topic {:prefix    :t
           :spec      ::topic
           :relations {:owner-id [:user :id]}}
   :post  {:prefix    :p
           :spec      ::post
           :relations {:topic-id [:topic :id]}}})

(defn ex-01
  []
  {:user  (gen/generate (s/gen ::user))
   :topic (gen/generate (s/gen ::topic))
   :post  (gen/generate (s/gen ::post))})

;;=>
{:user  {:id 2,  :username "G95seixU"},
 :topic {:id 11, :name "stA9xO50w", :owner-id 1},
 :post  {:id 57, :owner-id 2, :topic-id 2, :content "937x"}}

(defn ex-02
  []
  (:data (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})))

;; =>
{:nodeset #{:t0 :topic :p0 :u0 :post :user},
 :adj     {:post #{:p0}, :p0 #{:t0}, :topic #{:t0}, :t0 #{:u0}, :user #{:u0}},
 :in      {:p0 #{:post}, :t0 #{:topic :p0}, :u0 #{:t0 :user}},
 :attrs   {:post  {:type :ent-type},
           :p0    {:type                 :ent,
                   :index                0,
                   :ent-type             :post,
                   :query-term           [1],
                   :loom.attr/edge-attrs {:t0 {:relation-attrs #{:topic-id}}},
                   :spec-gen             {:id 2, :owner-id 7, :topic-id 16, :content "3IU"}},
           :topic {:type :ent-type},
           :t0    {:type                 :ent,
                   :index                0,
                   :ent-type             :topic,
                   :query-term           [:_],
                   :loom.attr/edge-attrs {:u0 {:relation-attrs #{:owner-id}}},
                   :spec-gen             {:id 16, :name "FM4fcV3t", :owner-id 2}},
           :user  {:type :ent-type},
           :u0    {:type       :ent,
                   :index      0,
                   :ent-type   :user,
                   :query-term [:_],
                   :spec-gen   {:id 2, :username "xh"}}}}

(defn ex-03
  []
  (-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
      (sm/attr-map :spec-gen)))

;; => 
{:p0 {:id 30, :owner-id 6, :topic-id 11, :content "03hK"}
 :t0 {:id 11, :name "A4rq01NK", :owner-id 84}
 :u0 {:id 84, :username "QN8J68"}}


(defn ex-04
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:post [[1]]}))

(ex-04)
;; =>
{:p0 {:id 2, :owner-id 20, :topic-id 2, :content "573AAM1D"}
 :t0 {:id 2, :name "6q7a4", :owner-id 2}
 :u0 {:id 2, :username "h"}}
