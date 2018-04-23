(ns reifyhealth.specmonstah-examples
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.core :as sg]
            [reifyhealth.specmonstah.test-data :as td]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [loom.io :as lio]))

(def view (comp lio/view :data #(sm/build-ent-db {:schema td/schema})))

(defn view
  "Extract common bits so people can focus on just the query"
  [query]
  (-> (sm/build-ent-db {:schema td/schema} query)
      :data
      lio/view))

(defn ex1 [] (view {:user [1]}))

(defn ex2 [] (view {:user [3]}))

(defn ex3 [] (view {:todo [3]}))

(defn ex4 [] (view {:todo-list-watch [3]}))
