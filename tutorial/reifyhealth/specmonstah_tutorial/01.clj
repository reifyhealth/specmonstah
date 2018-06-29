(ns reifyhealth.specmonstah-tutorial.01
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user {:prefix :u}})

(defn ex-01
  []
  (sm/build-ent-db {:schema schema} {:user [[3]]}))

(defn ex-02
  []
  (sm/build-ent-db {:schema schema} {:user [[:circe]]}))

(comment
  ;; evaluating this:
  (ex-01)

  ;; produces this:
  {:schema {:user {:prefix :u}}
   :data   {:nodeset #{:u1 :u0 :u2 :user}
            :adj     {:user #{:u1 :u0 :u2}}
            :in      {:u0 #{:user} :u1 #{:user} :u2 #{:user}}
            :attrs   {:user {:type :ent-type}
                      :u0   {:type :ent :index 0 :ent-type :user :query-term [3]}
                      :u1   {:type :ent :index 1 :ent-type :user :query-term [3]}
                      :u2   {:type :ent :index 2 :ent-type :user :query-term [3]}}}})
