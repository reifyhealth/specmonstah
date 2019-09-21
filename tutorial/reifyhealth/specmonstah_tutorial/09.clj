(ns reifyhealth.specmonstah-tutorial.09
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
           :relations {:topic-id [:topic :id]
                       :owner-id [:user :id]}}})

(def database (atom []))

(defn insert
  [db {:keys [ent-type visit-val spec-gen]}]
  (when-not visit-val
    (swap! database conj [ent-type spec-gen])))

(defn ex-01
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
      (sm/visit-ents :insert insert))
  @database)

(ex-01)
;; =>
[[:user {:id 7, :username "j29AFqnr"}]
 [:topic {:id 2, :name "Qqo04X1Zo", :owner-id 7}]
 [:post {:id 70, :owner-id 7, :topic-id 2, :content "al"}]]

(defn ex-02
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
      (sm/visit-ents :insert insert)
      (sg/ent-db-spec-gen {:post [[3]]})
      (sm/visit-ents :insert insert))
  @database)

;; =>
[[:user {:id 4, :username "pmLu0"}]
 [:topic {:id 3, :name "AM2O0uD", :owner-id 4}]
 [:post {:id 8, :owner-id 4, :topic-id 3, :content "yT7Y"}]
 [:user {:id 4, :username "pmLu0"}]
 [:topic {:id 3, :name "AM2O0uD", :owner-id 4}]
 [:post {:id 8, :owner-id 4, :topic-id 3, :content "yT7Y"}]]

(defn insert-once
  [db {:keys [ent-type spec-gen]}]
  (swap! database conj [ent-type spec-gen])
  true)

(defn ex-03
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
      (sm/visit-ents-once :insert insert-once)
      (sg/ent-db-spec-gen {:post [[3]]})
      (sm/visit-ents-once :insert insert-once))
  @database)

(ex-03)
;; =>
[[:user {:id 2, :username "MuGc6"}]
 [:topic {:id 87, :name "0Oj9P", :owner-id 2}]
 [:post {:id 2, :owner-id 2, :topic-id 87, :content "c6k3Z1HwI"}]
 [:post {:id 1, :owner-id 2, :topic-id 87, :content "qEjSKQ"}]
 [:post {:id 9, :owner-id 2, :topic-id 87, :content "646nn4bI"}]
 [:post {:id 45, :owner-id 2, :topic-id 87, :content "J2vL0Mgi"}]]
