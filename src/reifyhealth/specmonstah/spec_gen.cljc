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
  (if (contains? (relation-attr constraints) :coll)
    (update gen-data relation-attr #(conj % relation-val))
    (assoc gen-data relation-attr relation-val)))

(defn omit-relation?
  [db ent-name k]
  (let [{{ref k} :refs} (sm/query-opts db ent-name)]
    (sm/omit? ref)))

(defn reset-relations
  "The generated data will generate values agnostic of any constraints that may
  be present. This function updates values in the generated data to match up
  with constraints. First, it will remove any dummy ID's for a `:coll` relation.
  Next, it will remove any dummy ID's generated for an `:omit` relation. The
  updated ent-data map will be returned."
  [db ent-name ent-data]
  (let [acoll? (->> (sm/ent-schema db ent-name)
                    :constraints
                    (medley/filter-vals
                      (fn [attr-constraints] (contains? attr-constraints :coll)))
                    keys
                    set)]
    (into {}
          (comp (map (fn [[k v]] (if (acoll? k) [k []] [k v])))
                (map (fn [[k v]] (if (omit-relation? db ent-name k) [k nil] [k v]))))
          ent-data)))

(defn spec-gen-generate-ent-val
  "First pass function, uses spec to generate a val for every entity"
  [db ent-name ent-attr-key]
  (let [{:keys [spec spec-gen]} (sm/ent-schema db ent-name)]
    (merge (->> (gen/generate (s/gen spec))
                (reset-relations db ent-name))
           spec-gen
           (ent-attr-key (sm/query-opts db ent-name)))))

(defn spec-gen-assoc-relations
  "Look up referenced attributes and assign them"
  [{:keys [data] :as db} ent-name ent-attr-key]
  (let [{:keys [relations constraints spec-gen]} (sm/ent-schema db ent-name)]
    (reduce (fn [ent-data [referenced-ent relation-attr]]
              (assoc-relation ent-data
                              relation-attr
                              (get-in (lat/attr data referenced-ent ent-attr-key)
                                      (:path (sm/query-relation db ent-name relation-attr)))
                              constraints))
            (lat/attr data ent-name ent-attr-key)
            (sm/referenced-ent-attrs db ent-name))))

(def spec-gen [spec-gen-generate-ent-val spec-gen-assoc-relations])

(defn ent-db-spec-gen
  "Convenience function to build a new db using the spec-gen mapper
  and the default attr-key"
  [db query]
  (-> (sm/build-ent-db db query)
      (sm/visit-ents-once spec-gen-ent-attr-key spec-gen)))

(defn ent-db-spec-gen-attr
  "Convenience function to return a map of `{ent-name gen-data}` using
  the db returned by `ent-db-spec-gen`"
  [db query]
  (-> (ent-db-spec-gen db query)
      (sm/attr-map spec-gen-ent-attr-key)))
