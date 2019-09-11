(ns specmonstah-demo.components.app
  (:require [specmonstah-demo.examples.schemas :as schemas]
            [specmonstah-demo.components.vendor.ace]
            [sweet-tooth.frontend.form.components :as stfc]))

(defn app
  []
  [:div
   [:div.schema
    [:h2 "Schema"]
    (stfc/with-form [:schema]
      [input :ace :schema])]
   [:div.query "query"]
   [:div.spec-gen "spec-gen"]
   [:div.graph "graph"]])
