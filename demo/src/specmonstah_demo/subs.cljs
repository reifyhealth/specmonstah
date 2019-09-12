(ns specmonstah-demo.subs
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.paths :as p]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]

            [specmonstah-demo.examples.schemas :as schemas]))

(rf/reg-sub :query :query)

(rf/reg-sub :query-result-db
  :<- [:query]
  (fn [query]
    (when query
      (try (sg/ent-db-spec-gen {:schema schemas/todo-schema} query)
           (catch :default e nil)))))

(rf/reg-sub :query-result-attr-map
  :<- [:query-result-db]
  (fn [ent-db]
    (when ent-db
      (try (sm/attr-map ent-db :spec-gen)
           (catch :default e nil)))))

(rf/reg-sub :selected-node
  (fn [db]
    (:selected-node db)))

(rf/reg-sub :selected-node-details
  :<- [:query-result-db]
  :<- [:selected-node]
  (fn [[ent-db node]]
    (when (and ent-db node)
      (-> ent-db
          (sm/attr-map :spec-gen)
          (get node)))))
