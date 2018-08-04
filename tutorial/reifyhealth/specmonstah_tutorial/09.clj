(ns reifyhealth.specmonstah-tutorial.09
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [clojure.spec.alpha :as s]))

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

(def database (atom []))

(defn insert
  [db ent-name visit-key]
  (let [{:keys [spec-gen ent-type] :as attrs} (sm/ent-attrs db ent-name)]
    (when-not (visit-key attrs)
      (swap! database conj [ent-type spec-gen])
      true)))

(defn ex-01
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})
      (sm/visit-ents :insert insert))
  @database)

;; =>
[[:user {:id 6, :username "Ov0zaH57lTk86bAh"}]
 [:todo-list {:id 23, :name "9", :owner-id 6}]
 [:todo {:id 2, :details "hf", :todo-list-id 23}]]

(defn ex-02
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})
      (sm/visit-ents :insert insert)
      (sg/ent-db-spec-gen {:todo [[3]]})
      (sm/visit-ents :insert insert))
  @database)

;; =>
[[:user {:id 23, :username "0B1E5Iq4QWz4q"}]
 [:todo-list {:id 16, :name "gt", :owner-id 23}]
 [:todo {:id 17, :details "wN92", :todo-list-id 16}]
 [:todo {:id 3, :details "cQOav9DBqI8M57", :todo-list-id 16}]
 [:todo {:id 8, :details "Ek065tC78bD9wEJwLa", :todo-list-id 16}]
 [:todo {:id 5, :details "9", :todo-list-id 16}]]


(defn insert-once
  [db ent-name visit-key]
  (swap! database conj ((juxt :ent-type :spec-gen) (sm/ent-attrs db ent-name)))
  true)

(defn ex-03
  []
  (reset! database [])
  (-> (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})
      (sm/visit-ents-once :insert insert-once)
      (sg/ent-db-spec-gen {:todo [[3]]})
      (sm/visit-ents-once :insert insert-once))
  @database)

(ex-03)
;; =>
[[:user {:id 2, :username "S"}]
 [:todo-list {:id 2, :name "58Zb3p0Y75BJZS1m", :owner-id 2}]
 [:todo {:id 2, :details "OHqbuPz", :todo-list-id 2}]
 [:todo {:id 13, :details "3v7rllHTps1r6gN3", :todo-list-id 2}]
 [:todo {:id 27, :details "5nn24T029w7Z9KBUXE", :todo-list-id 2}]
 [:todo {:id 2, :details "zV37csm519blvP3r", :todo-list-id 2}]]
