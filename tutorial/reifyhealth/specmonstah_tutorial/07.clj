(ns reifyhealth.specmonstah-tutorial.07
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]
            [clojure.spec.alpha :as s]
            [reifyhealth.specmonstah.spec-gen :as sg]))

(s/def ::id (s/and pos-int? #(< % 100)))
(s/def ::not-empty-string (s/and string? not-empty #(< (count %) 20)))

(s/def ::username ::not-empty-string)
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::name ::not-empty-string)
(s/def ::owner-id ::id)
(s/def ::todo-list (s/keys :req-un [::id ::name ::owner-id]))

(s/def ::details ::not-empty-string)
(s/def ::todo-list-id ::id)
(s/def ::todo (s/keys :req-un [::id ::details ::todo-list-id]))

(def schema
  {:user      {:prefix :u
               :spec   ::user}
   :todo-list {:prefix    :tl
               :spec      ::todo-list
               :relations {:owner-id [:user :id]}}
   :todo      {:prefix    :t
               :spec     ::todo
               :relations {:todo-list-id [:todo-list :id]}}})

(defn ex-01
  []
  (sg/ent-db-spec-gen-attr {:schema schema}
                           {:user [[1 {:spec-gen {:username "bob"}}]]
                            :todo [[1 {:spec-gen {:details "get groceries"}}]]}))

(ex-01)
;; =>
{:tl0 {:id 48, :name "C8Cj51DSbZIb69Z", :owner-id 2}
 :t0  {:id 21, :details "get groceries", :todo-list-id 48}
 :u0  {:id 2, :username "bob"}}


(defn ex-02
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:todo-list [[1 {:refs {:owner-id ::sm/omit}}]]}))

(ex-02)
;; =>
{:tl0 {:id 2, :name "v"}}


(defn ex-03
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:todo-list [[1 {:refs     {:owner-id ::sm/omit}
                                                             :spec-gen {:owner-id nil}}]]}))

(ex-03)
;; =>
{:tl0 {:id 2, :name "v"}}
