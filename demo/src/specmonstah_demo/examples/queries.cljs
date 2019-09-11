(ns specmonstah-demo.examples.queries
  (:require [shadow.resource :as rc]))

(def queries
  [{:description "A project with multiple todo lists. Shows a :coll constraint."
    :query       "{:project [[:_ {:refs {:todo-list-ids 3}}]]}"}
   {:description ""}])
