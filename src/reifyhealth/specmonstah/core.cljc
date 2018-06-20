(ns reifyhealth.specmonstah.core
  (:require [loom.alg :as la]
            [loom.attr :as lat]
            [loom.graph :as lg]
            [loom.derived :as ld]
            [medley.core :as medley]
            [better-cond.core :as b]
            [clojure.test.check.generators :as gen :include-macros true]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [>]))

(s/def ::any (constantly true))
(s/def ::ent-type keyword?)
(s/def ::ent-name keyword?)
(s/def ::ent-attr keyword?)
(s/def ::ent-count pos-int?)
(s/def ::prefix keyword?)
(s/def ::constraint keyword?)
(s/def ::spec (s/and keyword? namespace))

;; schema specs
(s/def ::single-type-relation-path
  (s/cat :ent-type ::ent-type
         :path (s/+ ::any)))

(s/def ::polymorphic-relation-path
  (s/coll-of ::relation-path))

(s/def ::relation-path
  (s/or :single-type-relation-path ::single-type-relation-path
        :polymorphic-relation-path ::polymorphic-relation-path))

(s/def ::relations
  (s/map-of ::ent-attr ::relation-path))

;; This isn't used for validation, just for documentation
(s/def ::core-constraints
  #{:coll :uniq :required})

(s/def ::constraints
  (s/map-of ::ent-attr (s/coll-of ::constraint)))

(s/def ::ent-type-schema
  (s/keys :req-un [::prefix]
          :opt-un [::relations ::constraints ::spec]))

(s/def ::schema
  (s/map-of ::ent-type ::ent-type-schema))

;; query specs
(s/def ::coll-query-relations
  (s/or :ent-names (s/coll-of ::ent-name)
        :ent-count ::ent-count))

(s/def ::unary-query-relations
  (s/or :ent-name ::ent-name))

(s/def ::refs
  (s/map-of ::ent-attr (s/or :coll  ::coll-query-relations
                             :unary ::unary-query-relations)))

(s/def ::ref-types
  (s/map-of ::ent-attr ::ent-type))

(s/def ::bind
  (s/map-of ::ent-type ::ent-name))

(s/def ::query-opts
  (s/keys :opt-un [::refs ::ref-types ::bind]))

(s/def ::ent-id
  (s/or :ent-count ::ent-count
        :ent-name  ::ent-name))

(s/def ::query-term
  (s/or :n-1 (s/cat :ent-id ::ent-id)
        :n-2 (s/cat :ent-id ::ent-id
                    :query-opts ::query-opts)))

(s/def ::query
  (s/map-of ::ent-type (s/coll-of ::query-term)))

;; db specs
(s/def ::db
  (s/keys :req-un [::schema]))

(declare add-ent)

(defn relation-graph
  "A graph of the type dependencies in a schema. If entities of type
  `:project` reference an entity of type `:user` via `:owner-id`, then
  this will return a graph where the `:project` node connects to the
  `:user` node"
  [schema]
  (->> schema
       (medley/map-vals (fn [v] (->> v :relations vals (map first) set)))
       (lg/digraph)))

(defn ent-index
  "Used to keep track of entity's insertion order in graph relative to
  other entities of the same type."
  [g ent-type]
  (count (lg/successors g ent-type)))

(defn numeric-node-name
  "Template for generating a node name"
  [schema ent-type index]
  (let [prefix (get-in schema [ent-type :prefix])]
    (keyword (str (name prefix) index))))

(defn default-node-name
  "Whenever specmonstah needs to create a node that's not manually
  named, it uses this to generate the default name."
  [{:keys [schema]} ent-type]
  (numeric-node-name schema ent-type 0))

(defn incrementing-node-name
  "A template for creating distinct node names."
  [{:keys [data schema]} ent-type]
  (numeric-node-name schema ent-type (ent-index data ent-type)))

(defn add-edge-with-id
  "When indicating :ent-a references :ent-b, include a
  `:relation-attrs` graph attribute that includes the attributes via
  which `:ent-a` references `:ent-b`. 

  For example, if the `:project` named `:p0` has an `:owner-id` and
  `:updated-by-id` that both reference the `:user` named `:u0`, then
  the edge from `:p0` to `:u0` will if a `:relation-attrs` attribute
  with value `#{:owner-id :updated-by-id}`.

  This can be used e.g. to set the values for `:owner-id` and
  `:updated-by-id`."
  [g ent-name related-ent-name id]
  (let [ids (lat/attr g ent-name related-ent-name :relation-attrs)]
    (-> g
        (lg/add-edges [ent-name related-ent-name])
        (lat/add-attr ent-name related-ent-name :relation-attrs (conj (or ids #{}) id)))))

(defn bound-name
  "If `query-bindings` contains the binding `{:user :horton}` and the
  `relation-attr` `:owner-id` references a user, then `:owner-id`
  should reference `:horton`"
  [schema query-bindings ent-type relation-attr]
  (get query-bindings (get-in schema [ent-type :relations relation-attr 0])))

(defn bound-descendants?
  "Check whether `query-relations` contains bindings that apply to any
  descendants of `related-ent-type`"
  [{:keys [types schema relation-graph]} query-bindings related-ent-type]
  (not-empty (set/intersection (disj (set (lg/nodes (ld/subgraph-reachable-from relation-graph related-ent-type))) related-ent-type)
                               (set (keys query-bindings)))))

(defn bound-relation-attr-name-source
  [ent-name]
  (-> ent-name
      name
      (str/replace #"-\d+$" "")
      (str/replace #".*-bound-" "")
      (str/replace #"\d+$" "")))

(defn bound-relation-attr-name
  "Template for when a binding necessitates you add a new entity"
  [{:keys [schema]} ent-name related-ent-type index]
  (let [{:keys [prefix]} (related-ent-type schema)]
    (keyword (str (name prefix) "-bound-" (bound-relation-attr-name-source ent-name) "-" index))))

(defn related-ents
  "Returns all related ents for an ent's relation-attr"
  [{:keys [schema data] :as db} ent-name ent-type relation-attr query-term]
  (let [{:keys [relations constraints]}   (ent-type schema)
        attr-constraints                  (relation-attr constraints)
        {:keys [refs bind]}               (and query-term
                                               (-> (s/conform ::query-term query-term)
                                                   second
                                                   :query-opts))
        [qr-constraint [qr-type qr-term]] (relation-attr refs)
        related-ent-type                  (-> relations relation-attr first)]

    (cond (nil? qr-constraint) nil ;; noop
          
          (and (contains? attr-constraints :coll) (not= qr-constraint :coll))
          (throw (ex-info "Query-relations for coll attrs must be a number or vector"
                          {:spec-data (s/explain-data ::coll-query-relations qr-term)}))

          (and (not (contains? attr-constraints :coll)) (not= qr-constraint :unary))
          (throw (ex-info "Query-relations for unary attrs must be a keyword"
                          {:spec-data (s/explain-data ::unary-query-relations qr-term)})))
    
    (b/cond (= qr-type :ent-count) (mapv (partial numeric-node-name schema related-ent-type) (range qr-term))
            (= qr-type :ent-names) qr-term
            (= qr-type :ent-name)  [qr-term]
            :let [bn (bound-name schema bind ent-type relation-attr)]
            bn   [bn]
            
            :let [has-bound-descendants? (bound-descendants? db bind related-ent-type)
                  uniq?                  (contains? attr-constraints :uniq)
                  ent-index              (lat/attr data ent-name :index)]
            (and has-bound-descendants? uniq?) [(bound-relation-attr-name db ent-name related-ent-type ent-index)]
            has-bound-descendants?             [(bound-relation-attr-name db ent-name related-ent-type 0)]
            uniq?                              [(numeric-node-name schema related-ent-type ent-index)]
            related-ent-type                   [(default-node-name db related-ent-type)]
            :else                              [])))

(defn add-single-type-relation
  [db ent-name ent-type relation-path relation-attr query-term]
  (reduce (fn [db related-ent]
            (-> db
                (update :ref-ents conj [related-ent
                                        (:ent-type relation-path)
                                        (if-let [query-bindings (get-in query-term [1 :bind])]
                                          [:_ {:bind query-bindings}]
                                          [:_])])
                (update :data add-edge-with-id ent-name related-ent relation-attr)))
          db
          (related-ents db ent-name ent-type relation-attr query-term)))

(defn add-polymorphic-relation
  [{:keys [schema] :as db} ent-name ent-type polymorphic-relation-paths relation-attr query-term]
  (let [relation-type (or (get-in query-term [1 :ref-types relation-attr])
                          (get-in schema [ent-type :ref-types relation-attr]))
        relation-path (->> polymorphic-relation-paths
                           (map second)
                           (some #(and (= (:ent-type %) relation-type) %)))]
    (when-not relation-path
      (throw (ex-info "Could not determine type for polymorphic relation. Specify relation type under :ref-type key of query-opts, or specify default value in schema."
                      {:relation-attr relation-attr
                       :query-term query-term})))
    (add-single-type-relation db ent-name ent-type relation-path relation-attr query-term)))

(defn add-related-ents
  [{:keys [schema types data] :as db} ent-name ent-type query-term]
  (let [relation-schema (get-in schema [ent-type :relations])]
    (reduce (fn [db relation-attr]
              (let [[relation-type relation-path] (s/conform ::relation-path (get-in db [:schema ent-type :relations relation-attr]))]
                (case relation-type
                  :single-type-relation-path (add-single-type-relation db ent-name ent-type relation-path relation-attr query-term)
                  :polymorphic-relation-path (add-polymorphic-relation db ent-name ent-type relation-path relation-attr query-term))))
            db
            (keys relation-schema))))

(defn add-ent
  "Add an ent, and its related ents, to the ent-db"
  [{:keys [data] :as db} ent-name ent-type query-term]
  ;; don't try to add an ent if it's already been added
  (let [ent-name  (if (= ent-name :_) (incrementing-node-name db ent-type) ent-name)]
    ;; check both that the node exists and that it has the type
    ;; attribute: it's possible for the node to be added as an edge in
    ;; `add-related-ents`, without all the additional attributes below
    ;; to be added
    (if (and ((lg/nodes data) ent-name)
             (lat/attr data ent-name :type))
      db
      (-> db
          (update :data (fn [data]
                          (-> data
                              (lg/add-edges [ent-type ent-name])
                              (lat/add-attr ent-type :type :ent-type)
                              (lat/add-attr ent-name :type :ent)
                              (lat/add-attr ent-name :index (ent-index data ent-type))
                              (lat/add-attr ent-name :ent-type ent-type)
                              (lat/add-attr ent-name :query-term query-term))))
          (add-related-ents ent-name ent-type query-term)))))

(defn add-n-ents
  "Used when a query is something like [3]"
  [db ent-type num-ents query-term]
  (loop [db db
         n  num-ents]
    (if (zero? n)
      db
      (recur (add-ent db (incrementing-node-name db ent-type) ent-type query-term)
             (dec n)))))

(defn add-ent-type-query
  [db ent-type-query ent-type]
  (reduce (fn [db query-term]
            (let [[query-term-type ent-id] (:ent-id (second (s/conform ::query-term query-term)))]
              (case query-term-type
                :ent-count (add-n-ents db ent-type ent-id query-term)
                :ent-name  (add-ent db ent-id ent-type query-term))))
          db
          ent-type-query))

(defn add-ref-ents
  [db]
  (loop [{:keys [ref-ents] :as db} db]
    (if (empty? ref-ents)
      db
      (recur (reduce (fn [db [ent-name ent-type query-term]]
                       (add-ent db ent-name ent-type query-term))
                     (assoc db :ref-ents [])
                     ref-ents)))))

(defn init-db
  [{:keys [schema] :as db} query]
  (let [rg (relation-graph schema)]
    (-> db
        (update :data #(or % (lg/digraph)))
        (update :queries conj query)
        (assoc :relation-graph rg
               :types (set (keys schema))
               :ref-ents []))))

(defn throw-invalid-spec
  [arg-name spec data]
  (if-not (s/valid? spec data)
    (throw (ex-info (str arg-name " is invalid") {::s/explain-data (s/explain-data spec data)}))))

(defn identical-prefixes
  [schema]
  (->> (medley/map-vals :prefix schema)
       (reduce-kv (fn [grouping ent-type prefix]
                    (update grouping prefix (fn [x] (conj (or x #{}) ent-type))))
                  {})
       (medley/filter-vals #(clojure.core/> (count %) 1))))

(defn invalid-schema-relations
  "Relations that reference nonexistent types"
  [schema]
  (set/difference (->> schema
                       vals
                       ;; TODO clean this up
                       (map (comp (fn [relation-paths]
                                    (map (fn [relation-path]
                                           (if (set? relation-path)
                                             (map first relation-path)
                                             (first relation-path)))
                                         relation-paths))
                                  vals
                                  :relations))
                       flatten
                       set)
                  (set (keys schema))))

(defn invalid-constraints
  "Constraints that reference nonexistent relation attrs"
  [schema]
  (->> schema
       (medley/map-vals (fn [ent-schema]
                          (set/difference (set (keys (:constraints ent-schema)))
                                          (set (keys (:relations ent-schema))))))
       (medley/filter-vals not-empty)))

(defn build-ent-db
  "Produce a new db that contains all ents specified by query"
  [{:keys [schema] :as db} query]
  (let [isr (invalid-schema-relations schema)]
    (assert (empty? isr) (str "Your schema relations reference nonexistent types: " isr)))
  (let [prefix-dupes (identical-prefixes schema)]
    (assert (empty? prefix-dupes) (str "You have used the same prefix for multiple entity types: " prefix-dupes)))
  (let [ic (invalid-constraints schema)]
    (assert (empty? ic) (str "Schema constraints reference nonexistent relation attrs: " ic)))
  (throw-invalid-spec "db" ::db db)
  (throw-invalid-spec "query" ::query query)
  
  (let [db (init-db db query)]
    (->> (reduce (fn [db ent-type]
                   (if-let [ent-type-query (ent-type query)]
                     (add-ent-type-query db ent-type-query ent-type)
                     db))
                 db
                 (:types db))
         (add-ref-ents))))

;; -----------------
;; visiting
;; -----------------

(defn ents
  [{:keys [data]}]
  (lg/nodes (ld/nodes-filtered-by #(= (lat/attr data % :type) :ent) data)))

(defn attr-map
  "Produce a map where each key is a node and its value is a graph
  attr on that node"
  [{:keys [data] :as db} attr]
  (reduce (fn [m ent] (assoc m ent (lat/attr data ent attr)))
          {}
          (ents db)))

(defn ent-schema
  "Given an ent node, return the schema of its corresponding type"
  [{:keys [schema data]} ent-name]
  (get schema (lat/attr data ent-name :ent-type)))

(defn ent-related-by-attr?
  [data ent-name related-ent relation-attr]
  (and (contains? (lat/attr data ent-name related-ent :relation-attrs) relation-attr)
       related-ent))

(defn related-ents-by-attr
  [{:keys [data] :as db} ent-name relation-attr]
  (let [{:keys [constraints]} (ent-schema db ent-name)
        related-ents          (lg/successors data ent-name)]
    (if (contains? (relation-attr constraints) :coll)
      (->> related-ents
           (map #(ent-related-by-attr? data ent-name % relation-attr))
           (filter identity))
      (some #(ent-related-by-attr? data ent-name % relation-attr)
            related-ents))))

(defn query-opts
  [{:keys [data]} ent-name]
  (second (lat/attr data ent-name :query-term)))

(defn relation-attrs
  "Given an ent A and an ent it references B, return the set of attrs
  by which A references B"
  [{:keys [data]} ent-name referenced-ent]
  (lat/attr data ent-name referenced-ent :relation-attrs))

(defn referenced-ent-attrs
  "seq of [referenced-ent relation-attr]"
  [{:keys [data] :as db} ent-name]
  (for [referenced-ent (sort-by #(lat/attr data % :index) (lg/successors data ent-name))
        relation-attr  (relation-attrs db ent-name referenced-ent)]
    [referenced-ent relation-attr]))

(defn required-ents
  [db ent-name]
  (let [{:keys [constraints]} (ent-schema db ent-name)]
    (->> (medley/filter-vals :required constraints)
         keys
         (map #(related-ents-by-attr db ent-name %))
         set)))

(defn sort-by-required
  "If :a0 depends on :b0, returns the vector [:b0 :a0]"
  [{:keys [schema data] :as db} ents]
  (loop [ordered           []
         tried             #{}
         [ent & remaining] ents]
    (cond (nil? ent)  ordered

          (empty? (set/difference (required-ents db ent) (set ordered)))
          (recur (conj ordered ent) (conj tried ent) remaining)

          (contains? tried ent)
          (throw (ex-info "Can't order ents: check for a :required cycle"
                          {:ent ent :tried tried :remaining remaining}))
          
          :else
          (recur ordered (conj tried ent) (concat remaining [ent])))))

(defn visit-ents
  "Perform `visit-fns` on ents, storing return value as a graph
  attribute under `visit-key`"
  ([db visit-key visit-fns]
   (visit-ents db visit-key visit-fns (sort-by-required db (ents db))))
  ([db visit-key visit-fns ents]
   (let [visit-fns (if (sequential? visit-fns) visit-fns [visit-fns])]
     (reduce (fn [db [visit-fn ent]]
               (update db :data lat/add-attr ent visit-key (visit-fn db ent visit-key)))
             db
             (for [visit-fn visit-fns ent ents] [visit-fn ent])))))

(defn visit-ents-once
  "Like `visit-ents` but doesn't call `visit-fn` if the ent already
  has a `visit-key` attribute"
  ([db visit-key visit-fns]
   (visit-ents-once db visit-key visit-fns (sort-by-required db (ents db))))
  ([db visit-key visit-fns ents]
   (let [skip-ents (->> ents
                        (filter (fn [ent]
                                  (let [ent-attrs (get-in db [:data :attrs ent])]
                                    (contains? ent-attrs visit-key))))
                        (set))
         visit-fns (if (vector? visit-fns) visit-fns [visit-fns])]
     (visit-ents db
                 visit-key
                 (mapv (fn [visit-fn]
                         (fn [db ent visit-key]
                           (if (skip-ents ent)
                             (lat/attr (:data db) ent visit-key)
                             (visit-fn db ent visit-key))))
                       visit-fns)
                 ents))))

;; Viewing attributes
(defn >
  "Get attrs of node's children. Can be used to get all ents of a
  type."
  [{:keys [data]} node]
  (->> (lg/successors data node)
       (map (:attrs data))))

(defn q>
  "Get seq of nodes that have a type referenced in the query"
  [{:keys [data queries] :as db}]
  (let [query-types (->> queries first keys set)]
    (->> (ents db)
         (map (:attrs data))
         (filter #(query-types (:ent-type %))))))
