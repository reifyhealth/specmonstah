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
            [clojure.spec.alpha :as s]))

(s/def ::any (constantly true))
(s/def ::ent-type keyword?)
(s/def ::ent-name keyword?)
(s/def ::ent-attr keyword?)
(s/def ::ent-count int?)
(s/def ::prefix keyword?)
(s/def ::constraint keyword?)
(s/def ::spec (s/and keyword? namespace))

;; schema specs
(s/def ::relation-path
  (s/cat :ent-type ::ent-type
         :path (s/+ ::any)))

(s/def ::relations
  (s/map-of ::ent-attr ::relation-path))

(s/def ::constraints
  (s/map-of ::ent-attr ::constraint))

(s/def ::ent-type-schema
  (s/keys :req-un [::prefix]
          :opt-un [::relations ::constraints ::spec]))

(s/def ::schema
  (s/map-of ::ent-type ::ent-type-schema))

;; query specs
(s/def ::query-bindings
  (s/nilable (s/map-of ::ent-type ::ent-name)))


(s/def ::coll-query-relations
  (s/or :ent-names (s/coll-of ::ent-name)
        :ent-count ::ent-count))

(s/def ::unary-query-relations
  (s/or :ent-name ::ent-name))

(s/def ::query-relations
  (s/nilable (s/map-of ::ent-attr (s/or :coll  ::coll-query-relations
                                        :unary ::single-query-relations))))

(s/def ::extended-query-term
  (s/or :n-1 (s/cat :ent-name (s/nilable ::ent-name))
        :n-2 (s/cat :ent-name (s/nilable ::ent-name)
                    :query-relations ::query-relations)
        :n-3 (s/cat :ent-name (s/nilable ::ent-name)
                    :query-relations ::query-relations
                    :query-bindings ::query-bindings)
        :n-* (s/cat :ent-name (s/nilable ::ent-name)
                    :query-relations ::query-relations
                    :query-bindings ::query-bindings
                    :query-args (s/* ::any))))

(s/def ::query-term
  (s/nilable
    (s/or :ent-count ::ent-count
          :extended-query-term ::extended-query-term)))

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
      (str/replace #".*-bound-" "")))

(defn bound-relation-attr-name
  "Template for when a binding necessitates you add a new entity"
  [{:keys [schema]} ent-name related-ent-type index]
  (let [{:keys [prefix]} (related-ent-type schema)]
    (keyword (str (name prefix) "-bound-" (bound-relation-attr-name-source ent-name) "-" index))))

(defn related-ents
  "Returns all related ents for an ent's relation-attr"
  [{:keys [schema data] :as db} ent-name ent-type relation-attr query-term]
  (let [{:keys [relations constraints]}          (ent-type schema)
        constraint                               (relation-attr constraints)
        {:keys [query-relations query-bindings]} (-> (s/conform ::query-term query-term) second second)
        [qr-constraint [qr-type qr-term]]        (relation-attr query-relations)
        related-ent-type                         (-> relations relation-attr first)]

    (cond (nil? qr-constraint) nil
          
          (and (= constraint :has-many) (not= qr-constraint :coll))
          (throw (ex-info "Query-relations for has-many attrs must be a number or vector"
                          {:spec-data (s/explain-data ::has-many-query-relations qr-term)}))

          (and (not= constraint :has-many) (not= qr-constraint :unary))
          (throw (ex-info "Query-relations for has-one attrs must be a keyword"
                          {:spec-data (s/explain-data ::has-one-query-relations qr-term)})))
    
    (b/cond (= qr-type :ent-count) (mapv (partial numeric-node-name schema related-ent-type) (range qr-term))
            (= qr-type :ent-names) qr-term
            (= qr-type :ent-name)  [qr-term]
            :let [bn (bound-name schema query-bindings ent-type relation-attr)]
            bn   [bn]
            
            :let [has-bound-descendants? (bound-descendants? db query-bindings related-ent-type)
                  uniq?                  (= constraint :uniq)
                  ent-index              (lat/attr data ent-name :index)]
            (and has-bound-descendants? uniq?) [(bound-relation-attr-name db ent-name related-ent-type ent-index)]
            has-bound-descendants?             [(bound-relation-attr-name db ent-name related-ent-type 0)]
            uniq?                              [(numeric-node-name schema related-ent-type ent-index)]
            related-ent-type                   [(default-node-name db related-ent-type)]
            :else                              [])))

(defn add-related-ents
  [{:keys [schema types data] :as db} ent-name ent-type query-term]
  (let [relation-schema (get-in schema [ent-type :relations])]
    (reduce (fn [db relation-attr]
              (let [[relation-type] (get-in db [:schema ent-type :relations relation-attr])]
                (reduce (fn [db related-ent]
                          (-> db
                              (add-ent related-ent relation-type (if-let [query-bindings (get query-term 2)]
                                                                   [nil nil query-bindings]
                                                                   nil))
                              (update :data add-edge-with-id ent-name related-ent relation-attr)))
                        db
                        (related-ents db ent-name ent-type relation-attr query-term))))
            db
            (keys relation-schema))))

(defn add-ent
  "Add an ent, and its related ents, to the ent-db"
  [{:keys [data] :as db} ent-name ent-type query-term]
  ;; don't try to add an ent if it's already been added
  (let [ent-name (if (= ent-name :_) (incrementing-node-name db ent-type) ent-name)]
    (if ((lg/nodes data) ent-name)
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

(defn add-anonymous-ents
  [db ent-type num-ents]
  (loop [db db
         n  num-ents]
    (if (zero? n)
      db
      (recur (add-ent db (incrementing-node-name db ent-type) ent-type nil)
             (dec n)))))

(defn add-ent-type-query
  [db ent-type-query ent-type]
  (reduce (fn [db query-term]
            (let [[query-term-type annotated-query-term] (s/conform ::query-term query-term)]
              (case query-term-type
                :ent-count           (add-anonymous-ents db ent-type query-term)
                :extended-query-term (add-ent db
                                              (:ent-name (second annotated-query-term))
                                              ent-type
                                              query-term))))
          db
          ent-type-query))

(defn init-db
  [{:keys [schema] :as db}]
  (let [rg (relation-graph schema)]
    (-> db
        (update :data #(or % (lg/digraph)))
        (assoc :relation-graph rg
               :types (set (keys schema))
               :type-order (reverse (la/topsort rg))))))

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
       (medley/filter-vals #(> (count %) 1))))

(defn invalid-schema-relations
  [schema]
  (set/difference (->> schema
                       vals
                       (mapcat (comp #(map first %) vals :relations))
                       set)
                  (set (keys schema))))

(defn build-ent-db
  "Produce a new db that contains all ents specified by query"
  [{:keys [schema] :as db} query]
  (let [isr (invalid-schema-relations schema)]
    (assert (empty? isr) (str "Your schema relations reference nonexistent types: " isr)))
  (let [prefix-dupes (identical-prefixes schema)]
    (assert (empty? prefix-dupes) (str "You have used the same prefix for multiple entity types: " prefix-dupes)))
  (throw-invalid-spec "db" ::db db)
  (throw-invalid-spec "query" ::query query)
  
  (let [db (init-db db)]
    (reduce (fn [db ent-type]
              (if-let [ent-type-query (ent-type query)]
                (add-ent-type-query db ent-type-query ent-type)
                db))
            db
            (:type-order db))))

(defn ordered-ents
  "Given a db, returns all ents ordered first by type order, then by
  index."
  [{:keys [type-order data]}]
  (mapcat (fn [ent-type]
            (sort-by #(lat/attr data % :index)
                     (lg/successors data ent-type)))
          type-order))

(defn traverse-ents-add-attr
  "Traverse ents (no ent type nodes), adding val returned by `ent-fn`
  as an attr named `ent-attr-key`. Does not replace existing value of
  `ent-attr-key` for node."
  [db ent-attr-key attr-fn]
  (reduce (fn [{:keys [data] :as db} ent-node]
            (if (contains? (get-in data [:attrs ent-node]) ent-attr-key)
              db
              (update db :data lat/add-attr ent-node ent-attr-key (attr-fn db ent-node ent-attr-key))))
          db
          (ordered-ents db)))

(defn map-attr
  "Produce a map where each key is a node and its value is a graph
  attr on that node"
  [{:keys [data] :as db} attr]
  (reduce (fn [m ent] (assoc m ent (lat/attr data ent attr)))
          {}
          (ordered-ents db)))
