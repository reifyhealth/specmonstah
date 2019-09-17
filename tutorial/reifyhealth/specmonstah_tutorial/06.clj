(ns reifyhealth.specmonstah-tutorial.06
  (:require [reifyhealth.specmonstah.core :as sm]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
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
  {:user      (gen/generate (s/gen ::user))
   :todo-list (gen/generate (s/gen ::todo-list))
   :todo      (gen/generate (s/gen ::todo))})

;;=>
{:user      {:id 2, :username "qI0iNgiy"}
 :todo-list {:id 4, :name "etIZ3l6jDO7m9UR5P", :owner-id 11}
 :todo      {:id 1, :details "1K85jiEU3L366NTx1", :todo-list-id 2}}

(defn ex-02
  []
  (:data (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})))

;; =>
{:nodeset #{:todo-list :tl0 :t0 :u0 :todo :user},
 :adj {:todo #{:t0},
       :t0 #{:tl0},
       :todo-list #{:tl0},
       :tl0 #{:u0},
       :user #{:u0}},
 :in {:t0 #{:todo}, :tl0 #{:todo-list :t0}, :u0 #{:tl0 :user}},
 :attrs {:todo {:type :ent-type},
         :t0 {:type :ent,
              :index 0,
              :ent-type :todo,
              :query-term [1],
              :loom.attr/edge-attrs {:tl0 {:relation-attrs #{:todo-list-id}}},
              :spec-gen {:id 1, :details "uhr5LSa", :todo-list-id 8}},
         :todo-list {:type :ent-type},
         :tl0 {:type :ent,
               :index 0,
               :ent-type :todo-list,
               :query-term [:_],
               :loom.attr/edge-attrs {:u0 {:relation-attrs #{:owner-id}}},
               :spec-gen {:id 8, :name "xbamqBULZ", :owner-id 42}},
         :user {:type :ent-type},
         :u0 {:type :ent,
              :index 0,
              :ent-type :user,
              :query-term [:_],
              :spec-gen {:id 42, :username "abrfR4s1I15"}}}}

(defn ex-03
  []
  (-> (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})
      (sm/attr-map :spec-gen)))

;; => 
{:tl0 {:id 21, :name "0N2xKMNwM8uO", :owner-id 19}
 :t0  {:id 4, :details "PGf92", :todo-list-id 21}
 :u0  {:id 19, :username "fz774"}}


(defn ex-04
  []
  (sg/ent-db-spec-gen-attr {:schema schema} {:todo [[1]]}))

(ex-04)
;; =>
{:tl0 {:id 51, :name "VO1161Id66DJRftxq", :owner-id 90}
 :t0  {:id 91, :details "qaQ0e5Bfa6B", :todo-list-id 51}
 :u0  {:id 90, :username "82d71j551NVMFj4"}}
