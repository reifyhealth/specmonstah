(ns reifyhealth.specmonstah.generate
  "Generate data for specmonstah queries using various generation methods.

  Generation can be specified in the schema, or the query. the former taking
  precedent over the latter.

  Generation Methods:
  - `:spec`      - a clojure spec, same as used by spec-gen.
  - `:generator` - a test check generator.
  - `:fn`        - a function called with no arguments to generate the entity.
  - `:malli`     - a malli schema.
    - NOTE: For malli support, include `metosin/malli` in your project
            dependencies and load the namespace
            `reifyhealth.specmonstah.generate.malli`.
  - Other generation methods maybe be added by adding methods to
    `generate-entity`."
  (:require [clojure.data :as data]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.test.check.generators :as gen]
            [reifyhealth.specmonstah.core :as sm]))

(def generate-visit-key ::generated)

;; Generation Strategies

(defmulti generate-entity
  "Generate an entity using the specified strategy"
  (fn [strategy _argument] strategy)
  :default ::default)

;; Use a function to generate the entity
;; ex. In a schema: {:users {:fn gen-user, :prefix :u}}
;;     In a query:  {:users [[1 {:fn gen-user}]]}
(defmethod generate-entity :fn [_ f] (f))

;; test.check generators
(defmethod generate-entity :generator
  [_ generator]
  (gen/generate generator))

;; spec, same as spec-gen
(defmethod generate-entity :spec
  [_ spec]
  (-> spec s/gen sgen/generate))

;; Visitor Functions
(defn visit-fn-generate
  "Select and invoke a generator for visited entities"
  [strategies]
  (fn [db {:keys [ent-schema query-opts]}]
    (let [strategies     (or (seq strategies) (keys (methods generate-entity)))
          [strategy arg] (some (fn [opts]
                                 (some (fn [strat]
                                         (when-let [arg (get opts strat)]
                                           [strat arg]))
                                       strategies))
                               ;; Check query before schema
                               [query-opts ent-schema])]
      (if strategy
        (generate-entity strategy arg)
        (throw (ex-info "No suitable generation strategy found for entity"
                        {:schema     ent-schema
                         :query      query-opts
                         :strategies strategies}))))))

(defn visit-fn-overwrites
  "Overwrites generated data with what's found in `:set` key of the schema and
  the query in that order"
  [db {:keys [ent-schema
              query-opts
              visit-val]}]
                             ;; check `:spec-gen` after `:set` for
                             ;; backwards compatibility with spec-gen
  (let [schema-overrides (or (:set ent-schema) (:spec-gen ent-schema))
        query-overrides  (or (:set query-opts) (:spec-gen query-opts))
        merged           (cond-> visit-val
                           ;; the schema can include vals to merge into each ent
                           (fn? schema-overrides)  schema-overrides
                           (map? schema-overrides) (merge schema-overrides)
                           ;; query opts can also specify merge vals
                           (fn? query-overrides)   query-overrides
                           (map? query-overrides)  (merge query-overrides))
        changed-keys     (->> (data/diff visit-val merged)
                              (take 2)
                              (map keys)
                              (apply into)
                              (set))]
    (with-meta merged {::sm/overwritten changed-keys})))

(defn visit-fn-insert
  "Invoke insertion function after generation has occured."
  [insert-fn]
  (fn [db {:keys [ent-type visit-val]}]
    (insert-fn db ent-type visit-val)
    visit-val))

;; User Interface

(defn generate
  "Generate entities according to a query using various data generation
  methods. See `reifyhealth.specmonstah.generate/generate-entity` for
  more information.

  Options:

  - `:insert!` - a side effect function called on an entity after it's been
  generated. (insert! entity-type entity)

  - `:strategies` - only use the specified generation strategies in the
  specified order."
  ([db query]
   (generate db query nil))
  ([db query & {:keys [insert! strategies]
                :as   options}]
   (let [visit-fn (cond-> [(visit-fn-generate strategies)
                           sm/reset-relations
                           visit-fn-overwrites
                           sm/assoc-referenced-vals]
                    insert! (conj (visit-fn-insert insert!)))]
     (-> (sm/add-ents db query)
         (sm/visit-ents-once generate-visit-key visit-fn)))))

(defn attrs
  "get the generated values for a database"
  [db]
  (sm/attr-map db generate-visit-key))

(comment
  ;; Example
  (-> (generate {:schema {:user {:malli [:map
                                         [:id :int]
                                         [:updated-by-id :int]
                                         [:user-name :string]]
                                 :prefix :u
                                 :relations {:updated-by-id [:user :id]}}}}
                {:user [[1 {:set {:user-name "Omar"}}]]})
      attrs)
  '=> {:u0 {:id 34, :updated-by-id 34, :user-name "Omar"}})
