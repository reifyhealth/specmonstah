(ns specmonstah-demo.components.vendor.ace
  (:require ["react-ace" :default AceEditor]
            [reagent.core :as r]
            [sweet-tooth.frontend.form.components :as stfc]))

(defmethod stfc/input-type-opts :ace
  [opts]
  (-> (stfc/input-type-opts-default opts)
      (dissoc :type)))

(defmethod stfc/input :ace
  [{:keys [partial-form-path attr-path value]}]
  [:> AceEditor {:onChange (fn [val] (stfc/dispatch-change partial-form-path attr-path val))
                 :name     "ACE BABY"
                 :value    (or value "")
                 :mode     "clojure"
                 :theme    "monokai"}])
