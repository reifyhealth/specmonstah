(ns reifyhealth.specmonstah-tutorial.07
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]
            [clojure.spec.alpha :as s]
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
  (sg/ent-db-spec-gen-attr {:schema schema}
                           {:user [[1 {:spec-gen {:username "barb"}}]]
                            :post [[1 {:spec-gen {:content "so good to be barb"}}]]}))

(ex-01)
;; =>
{:p0 {:id 81, :owner-id 96, :topic-id 68, :content "so good to be barb"}
 :t0 {:id 68, :name "3l1IR8", :owner-id 3}
 :u0 {:id 3, :username "barb"}}


(defn ex-02
  []
  (sg/ent-db-spec-gen-attr {:schema schema}
                           {:topic [[1 {:refs {:owner-id ::sm/omit}}]]}))

(ex-02)
;; =>
{:tl0 {:id 2, :name "v"}}


(defn ex-03
  []
  (sg/ent-db-spec-gen-attr {:schema schema}
                           {:topic [[1 {:refs     {:owner-id ::sm/omit}
                                        :spec-gen {:owner-id nil}}]]}))

(ex-03)
;; =>
{:tl0 {:id 2, :name "v"}}
