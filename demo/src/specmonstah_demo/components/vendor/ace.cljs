(ns specmonstah-demo.components.vendor.ace
  (:require ["react-ace" :default AceEditor]
            [sweet-tooth.frontend.form.components :as stfc]
            ["brace/mode/clojure"]
            ["brace/theme/monokai"]))

(defmethod stfc/input-type-opts :ace
  [opts]
  (-> (stfc/input-type-opts-default opts)
      (dissoc :type)))

(defmethod stfc/input :ace
  [{:keys [partial-form-path attr-path value height width]}]
  [:> AceEditor {:onChange (fn [val] (stfc/dispatch-change partial-form-path attr-path val))
                 :name     "ACE BABY"
                 :value    (or value "")
                 :height   (or height "300px")
                 :width    (or width "300px")
                 :theme    "monokai"
                 :mode     "clojure"}])

(defn ace-readonly
  [text opts]
  [:> AceEditor (merge {:name     "ace readonly"
                        :value    (or text "")
                        :theme    "monokai"
                        :mode     "clojure"
                        :readOnly true}
                       opts)])
