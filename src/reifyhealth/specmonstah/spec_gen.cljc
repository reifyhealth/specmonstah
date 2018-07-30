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
  [db ent-name ent-attr-key]
  (let [{{ref ent-attr-key} :refs} (sm/query-opts db ent-name)]
    (sm/omit? ref)))

(defn reset-relations
  "The generated data will generate values agnostic of any constraints that may
  be present. This function updates values in the generated data to match up
  with constraints. First, it will remove any dummy ID's for a `:coll` relation.
  Next, it will remove any dummy ID's generated for an `:omit` relation. The
  updated ent-data map will be returned."
  [db ent-name ent-data]
  (let [coll-attrs (sm/relation-attrs-with-constraint db ent-name :coll)]
    (into {}
          (comp (map (fn [[k v]] (if (coll-attrs k) [k []] [k v])))
                (map (fn [[k v]] (if-not (omit-relation? db ent-name k) [k v]))))
          ent-data)))

(defn spec-gen-generate-ent-val
  "First pass function, uses spec to generate a val for every entity"
  [db ent-name _ent-attr-key]
  (let [{:keys [spec]} (sm/ent-schema db ent-name)]
    (->> (gen/generate (s/gen spec))
         (reset-relations db ent-name))))

(defn spec-gen-assoc-relations
  "Next, look up referenced attributes and assign them"
  [{:keys [data] :as db} ent-name ent-attr-key]
  (let [{:keys [constraints]} (sm/ent-schema db ent-name)]
    (reduce (fn [ent-data [referenced-ent relation-attr]]
              (assoc-relation ent-data
                              relation-attr
                              (get-in (lat/attr data referenced-ent ent-attr-key)
                                      (:path (sm/query-relation db ent-name relation-attr)))
                              constraints))
            (lat/attr data ent-name ent-attr-key)
            (sm/referenced-ent-attrs db ent-name))))

(defn spec-gen-merge-overwrites
  "Finally, merge any overwrites specified in the schema or query"
  [{:keys [data] :as db} ent-name ent-attr-key]
  (let [{:keys [spec-gen]} (sm/ent-schema db ent-name)]
    (merge (lat/attr data ent-name ent-attr-key)
           spec-gen
           (ent-attr-key (sm/query-opts db ent-name)))))

(def spec-gen [spec-gen-generate-ent-val
               spec-gen-assoc-relations
               spec-gen-merge-overwrites])

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
