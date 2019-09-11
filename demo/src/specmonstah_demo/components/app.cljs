(ns specmonstah-demo.components.app
  (:require [re-frame.core :as rf]
            [cljs.pprint :as pprint]
            [sweet-tooth.frontend.form.components :as stfc]
            [sweet-tooth.frontend.core.utils :as stcu]

            [specmonstah-demo.examples.schemas :as schemas]
            [specmonstah-demo.components.vendor.ace :as ace]
            [specmonstah-demo.components.vendor.vis :as vis]))

(defn spec-gen
  "Show the attrs generated by spec"
  []
  [:div.spec-gen
   [:h2 "Generated Attributes"]
   [:p "The maps generated by clojure.spec for each ent"]
   [:div
    [ace/ace-readonly
     (with-out-str (pprint/pprint @(rf/subscribe [:query-result-attr-map])))
     {:width "100%"}]]])

(defn query-form
  []
  [:div.query
   [:h2 "Query"]
   [:p "The Specmonstah query is used to specify the data you want to generate"]
   (stfc/with-form [:query]
     [:form {:on-submit (stcu/prevent-default #(rf/dispatch [:submit-query]))}
      [input :ace :query {:height "100px"
                          :width  "100%"}]
      [:input {:type "submit" :value "Run"}]])])

(defn app
  []
  [:div.app
   [:div.input
    [query-form]
    [:div.schema
     [:h2 "Schema"]
     [ace/ace-readonly
      schemas/todo-schema-txt
      {:width "100%"}]]]
   [:div.output
    [:div.graph
     [:h2 "Graph"]
     (if-let [db @(rf/subscribe [:query-result-db])]
       [vis/graph db {:options {:height "500px"}}]
       [:p "When you run a query a visualization will appear here"])]
    [spec-gen]]])