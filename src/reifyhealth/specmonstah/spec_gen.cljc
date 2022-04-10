(ns reifyhealth.specmonstah.spec-gen
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.data :as data]
   [reifyhealth.specmonstah.core :as sm]))

(def spec-gen-visit-key :spec-gen)

(def spec-gen
  (sm/wrap-gen-data-visiting-fn
   (fn [db {:keys [ent-name]}]
     (-> db
         (sm/ent-schema ent-name)
         :spec
         s/gen
         gen/generate))))

(defn ent-db-spec-gen
  "Convenience function to build a new db using the spec-gen mapper
  and the default attr-key"
  [db query]
  (-> (sm/add-ents db query)
      (sm/visit-ents-once spec-gen-visit-key spec-gen)))

(defn ent-db-spec-gen-attr
  "Convenience function to return a map of `{ent-name gen-data}` using
  the db returned by `ent-db-spec-gen`"
  [db query]
  (-> (ent-db-spec-gen db query)
      (sm/attr-map spec-gen-visit-key)))


;; -----------------
;; deprecated fns
;; -----------------

;; the functions below have been refactored and move to
;; reifyhealth.specmonstah.core.

(defn ^:deprecated omit-relation?
  [db ent-name reference-key]
  (sm/omit? (get-in (sm/query-opts db ent-name) [:refs reference-key])))

(defn ^:deprecated reset-relations
  "The generated data will generate values agnostic of any constraints that may
  be present. This function updates values in the generated data to match up
  with constraints. First, it will remove any dummy ID's for a `:coll` relation.
  Next, it will remove any dummy ID's generated for an `:omit` relation. The
  updated ent-data map will be returned."
  [db ent-name ent-data]
  (let [coll-attrs (sm/relation-attrs-with-constraint db ent-name :coll)]
    (into {}
          (comp (map (fn [[k v]] (if (coll-attrs k) [k []] [k v])))
                (map (fn [[k v]] (when-not (omit-relation? db ent-name k) [k v]))))
          ent-data)))

(defn ^:deprecated spec-gen-generate-ent-val
  "First pass function, uses spec to generate a val for every entity"
  [db {:keys [ent-name]}]
  (let [{:keys [spec]} (sm/ent-schema db ent-name)]
    (->> (gen/generate (s/gen spec))
         (reset-relations db ent-name))))

(defn ^:deprecated spec-gen-generate-ent-
  "First pass function, uses spec to generate a val for every entity"
  [db {:keys [ent-name]}]
  (let [{:keys [spec]} (sm/ent-schema db ent-name)]
    (->> (gen/generate (s/gen spec))
         (reset-relations db ent-name))))

(defn ^:deprecated assoc-relation
  "Look up related ent's attr value and assoc with parent ent
  attr. `:coll` relations will add value to a vector."
  [gen-data relation-attr relation-val constraints]
  (if (contains? (relation-attr constraints) :coll)
    (update gen-data relation-attr #((fnil conj []) % relation-val))
    (assoc gen-data relation-attr relation-val)))

(defn ^:deprecated spec-gen-assoc-relations
  "Next, look up referenced attributes and assign them"
  [db {:keys [ent-name visit-key visit-val]}]
  (let [{:keys [constraints]} (sm/ent-schema db ent-name)
        skip-keys             (:overwritten (meta visit-val) #{})]
    (->> (sm/referenced-ent-attrs db ent-name)
         (filter (comp (complement skip-keys) second))
         (reduce (fn [ent-data [referenced-ent relation-attr]]
                   (assoc-relation ent-data
                                   relation-attr
                                   (get-in (sm/ent-attr db referenced-ent visit-key)
                                           (:path (sm/query-relation db ent-name relation-attr)))
                                   constraints))
                 visit-val))))

(defn ^:deprecated spec-gen-merge-overwrites
  "Merge any overwrites specified in the schema or query"
  [_db {:keys [visit-val visit-query-opts schema-opts]}]
  (let [merged       (cond-> visit-val
                       (fn? schema-opts)       schema-opts
                       (map? schema-opts)      (merge schema-opts)
                       (fn? visit-query-opts)  visit-query-opts
                       (map? visit-query-opts) (merge visit-query-opts))
        changed-keys (->> (data/diff visit-val merged)
                          (take 2)
                          (map keys)
                          (apply into)
                          (set))]
    (with-meta merged {:overwritten changed-keys})))
