(ns specmonstah-demo.components.app
  (:require [re-frame.core :as rf]
            [cljs.pprint :as pprint]
            [specmonstah-demo.examples.schemas :as schemas]
            [specmonstah-demo.components.vendor.ace :as ace]
            [sweet-tooth.frontend.form.components :as stfc]
            [sweet-tooth.frontend.core.utils :as stcu]))

(defn spec-gen
  []
  [:div.spec-gen "spec-gen"
   [ace/ace-readonly
    (with-out-str (pprint/pprint @(rf/subscribe [:query-result])))
    {:width "40%"}]])

(defn query-form
  []
  [:div.query "query"
   (stfc/with-form [:query]
     [:form {:on-submit (stcu/prevent-default #(rf/dispatch [:submit-query]))}
      [input :ace :query {:height "100px"
                          :width  "40%"}]
      [:input {:type "submit"}]])])

(defn app
  []
  [:div
   [:div.schema
    [:h2 "Schema"]
    [ace/ace-readonly
     schemas/todo-schema-txt
     {:width "40%"}]]
   [query-form]
   [spec-gen]
   [:div.graph "graph"]])
