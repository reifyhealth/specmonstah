(ns specmonstah-demo.subs
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.paths :as p]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]

            [specmonstah-demo.examples.schemas :as schemas]))

(rf/reg-sub :query :query)

(rf/reg-sub :query-result
  :<- [:query]
  (fn [query]
    (when query
      (try (-> (sg/ent-db-spec-gen {:schema schemas/todo-schema} query)
               (sm/attr-map :spec-gen))
           (catch :default e nil)))))
