(ns reifyhealth.specmonstah.spec-gen
  (:require [loom.attr :as lat]
            [loom.graph :as lg]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [medley.core :as medley]
            [reifyhealth.specmonstah.core :as sm]))

(def spec-gen-ent-attr-key :spec-gen)

(s/def ::ent-attrs (s/map-of ::sm/ent-attr ::sm/any))

(defn assoc-relation
  "Look up related ent's attr value and assoc with parent ent
  attr. `:coll` relations will add value to a vector."
  [gen-data relation-attr relation-val constraints]
  (if (= :coll (relation-attr constraints))
    (update gen-data relation-attr #(conj % relation-val))
    (assoc gen-data relation-attr relation-val)))

(defn reset-coll-relations
  "For ents that have have a `:coll` relation, the associated spec
  might generate a bunch of dummy IDs. This replaces those with an empty
  vector."
  [ent-data constraints]
  (reduce (fn [ent-data hm-key] (assoc ent-data hm-key []))
          ent-data
          (keys (medley/filter-vals (fn [v] (= v :coll)) constraints))))

(defn gen-ent-data
  [{:keys [spec constraints]}]
  (-> (gen/generate (s/gen spec))
      (reset-coll-relations constraints)))

(defn spec-gen
  "A mapping function that uses spec to generate data for each ent,
  and uses the ent's edges to set values for relation attrs.

  You can specify a map to merge into your spec-generated map under
  the `:spec-gen` key of the second arg in a query term, e.g.

  `[:todo0 {:spec-gen {:attr-1 val-1}}]`"
  [{:keys [schema]} ent-name ent-attr-key]
  (let [ent-type-schema                          (get schema (lat/attr data ent-name :ent-type))
        {:keys [relations constraints spec-gen]} ent-type-schema]
    [(fn [{:keys [data]}]
       (merge (gen-ent-data ent-type-schema)
              spec-gen
              (get-in (lat/attr data ent-name :query-term) [1 ent-attr-key])))
     (fn [{:keys [data]}]
       (let [attr (lat/attr data ent-name ent-attr-key)]
         (reduce (fn [ent-data referenced-ent]
                   (reduce (fn [ent-data relation-attr]
                             (assoc-relation ent-data
                                             relation-attr
                                             (get (lat/attr data referenced-ent ent-attr-key)
                                                  (get-in relations [relation-attr 1]))
                                             constraints))
                           ent-data
                           (lat/attr data ent-name referenced-ent :relation-attrs)))
                 attr
                 (sort-by #(lat/attr data % :index)
                          (lg/successors data ent-name)))))]))

(defn ent-db-spec-gen
  "Convenience function to build a new db using the spec-gen mapper
  and the default attr-key"
  [db query]
  (-> (sm/build-ent-db db query)
      (sm/map-ents-attr-once spec-gen-ent-attr-key spec-gen)))

(defn ent-db-spec-gen-attr
  "Convenience function to return a map of `{ent-name gen-data}` using
  the db returned by `ent-db-spec-gen`"
  [db query]
  (-> (ent-db-spec-gen db query)
      (sm/attr-map spec-gen-ent-attr-key)))
