(ns specmonstah-demo.components.app
  (:require [specmonstah-demo.examples.schemas :as schemas]))

(defn app
  []
  [:div
   [:div.schema
    [:pre (str schemas/todo-schema)]]
   [:div.query "query"]
   [:div.spec-gen "spec-gen"]
   [:div.graph "graph"]])
