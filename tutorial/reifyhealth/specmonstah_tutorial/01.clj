(ns reifyhealth.specmonstah-tutorial.01
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user {:prefix :u}})

(defn ex-01
  []
  (sm/add-ents {:schema schema} {:user [[3]]}))

(-> (ex-01) (sm/ents-by-type))

(-> (ex-01) (sm/ent-relations :u0))

(-> (ex-01) (sm/all-ent-relations))

(comment
  ;; evaluating this:
  (ex-01)

  ;; produces this:
  {:schema         {:user {:prefix :u}}
   :data           {:nodeset #{:u1 :u0 :u2 :user}
                    :adj     {:user #{:u1 :u0 :u2}}
                    :in      {:u0 #{:user} :u1 #{:user} :u2 #{:user}}
                    :attrs   {:user {:type :ent-type}
                              :u0   {:type :ent :index 0 :ent-type :user :query-term [3]}
                              :u1   {:type :ent :index 1 :ent-type :user :query-term [3]}
                              :u2   {:type :ent :index 2 :ent-type :user :query-term [3]}}}
   :queries        [{:user [[3]]}]
   :relation-graph {:nodeset #{:user} :adj {} :in {}}
   :types          #{:user}
   :ref-ents       []})
