(ns reifyhealth.specmonstah.core
  (:require [loom.graph :as g]
            [loom.alg :as la]
            [medley.core :as medley])
  (:refer-clojure :exclude [doall]))

(def ^:private query-term-arity 4)

(defn- expand-default-refs
  [default-refs]
  (medley/map-vals (fn [v] (if (vector? v)
                            (let [[relation-type field] v]
                              [relation-type ::template field])
                            v))
                   default-refs))

(defn expand-relation-template
  "Allows you to write a template in a compact format, like 
  
  ```
  (require '[reify.specmonstah :as rs])
  (def relation-template
   {::author [{}]
    ::publisher [{}]
    ::book [{:author-id [::author :id]
             :publisher-id [::publisher :id]}]
    ::chapter [{:book-id [::book :id]}]})

  (rs/expand-relation-template relation-template)
  ; => 
  {::author {::rs/template [{} nil]}
   ::publisher {::rs/template [{} nil]}
   ::book {::rs/template [{:author-id [::author ::rs/template :id]
                           :pubisher-id [::publisher ::rs/template :id]}
                          nil]}
   ::chapter {::rs/template [{:book-id [::book ::rs/template :id]} nil]}}
  ```"
  [relation-template]
  (reduce-kv (fn [result relation-type [default-refs default-attrs]]
               (assoc result relation-type {::template [(expand-default-refs default-refs) default-attrs]}))
             {}
             relation-template))

(defn- ref-names
  [xs]
  (medley/map-vals #(if (vector? %) (first %) %) xs))

(defn- ent-references
  "Returns `[ent-type ent-name]` pairs from a ref map"
  [refs]
  (->> refs
       vals
       (map #(vec (take 2 %)))
       set))

(defn- topo
  "takes ref paths `[ent-type ent-name]` to produce a DAG of
  dependencies"
  [relations]
  (reduce-kv (fn [graph ent-type ents]
               (reduce-kv (fn [graph ent-name ent]
                            (assoc graph [ent-type ent-name] (ent-references (first ent))))
                          graph
                          ents))
             {}
             relations))

(defn- references
  "Pull references from default ent, accessed by [relation-type
  relation-name], merged with custom attributes from query. Expects a
  gen-formatted query."
  [query]
  (reduce (fn [refs term] (into refs (ent-references (second term))))
          #{}
          query))

(defn- selected-ents
  "Use a query to select which entities from `relations` should be
  used, sorted topographically. This way, if Entity A references
  Entity B, we can insert Entity B before Entity A. Expects a
  gen-formatted query."
  [relations query]
  (let [graph (-> relations
                  topo
                  g/digraph)]
    (->> (references query)
         (map #(la/bf-traverse graph %))
         (reduce into [])
         (g/subgraph graph)
         (la/topsort)
         reverse)))

(defn- merge-template-refs
  "(merge-template-refs {:author-id [::author ::template :id]} {:author-id :a1})
   => {:author-id [::author :a1 :id]}"
  [template refs]
  (merge-with (fn [template-relation new-relation-name]
                (assoc template-relation 1 new-relation-name))
              template
              refs))

(defn- add-query-term-relations
  "Recusively unpacks the query DSL, inserting referenced entities. For example,
  if the query includes the term
  
  [::book {:author-id :a1}]
  
  Then this function will insert a copy of 
  (get-in relations [::author ::template]) at [::author :a1].

  Also merges all attributes specified for referenced entities"
  [[ent-type refs] relations]
  (reduce-kv (fn [relations ent-field ref-name]
               (let [field-ref-type (get-in relations [ent-type ::template 0 ent-field 0])
                     ref-template (get-in relations [field-ref-type ::template])]
                 (when-not field-ref-type
                   (throw (ex-info (str "The relation " ent-field " for " ent-type " does not exist")
                                   {:ent-type ent-type
                                    :ent-field ent-field})))
                 (if (vector? ref-name)
                   ;; ref recursively queries its own refs
                   (let [[ref-name ref-refs ref-attrs] ref-name]
                     (add-query-term-relations [field-ref-type ref-refs]
                                               (assoc-in relations
                                                         [field-ref-type ref-name]
                                                         [(merge-template-refs (first ref-template) (ref-names ref-refs))
                                                          (merge (second ref-template)
                                                                 (get-in relations [field-ref-type ref-name 1])
                                                                 ref-attrs)])))
                   (assoc-in relations [field-ref-type ref-name] ref-template))))
             relations
             refs))

(defn- add-query-relations
  "Takes a raw query and adds all the entities referenced"
  [relations query]
  (reduce (fn [relations query-term] (add-query-term-relations query-term relations))
          relations
          query))

(defn- flatten-query
  "Ensures that every query term is of the form `[spec refs]`,
  where `refs` is a map.

  Also ensures that `refs` is only one level deep, so
  [[::chapter {:book-id [:b1 {:author-id :a1}]}]] becomes
  [[::chapter {:book-id :b1}]]"
  [relations query]
  (mapv (fn [term]
          (let [[ent-type refs attrs] term]
            [ent-type
             (ref-names refs)
             attrs]))
        query))

(defn- merge-query-refs
  "Merges a query term's custom refs with that entity's template"
  [relations query]
  (mapv (fn [[ent-type refs attrs]]
          [ent-type
           (let [template-refs (get-in relations [ent-type ::template 0])]
             (merge-template-refs template-refs refs))
           attrs])
        query))

(defn empty-ref?
  [ref]
  (and (vector? ref)
       (empty? (second ref))
       (empty? (last ref))))

(defn- query-term-bindings
  [query-term]
  (->> (second query-term)
       (medley/filter-keys vector?)
       (medley/map-keys (fn [k] (if (= (count k) 2)
                                 [(first k) ::template (second k)]
                                 k)))))

(defn- remove-query-term-bindings
  [query-term]
  (update query-term 1 (partial medley/filter-keys (complement vector?))))

(defn- bind-term-relations
  "Ensures that relations are consistent across a query term's tree"
  ([relations query-term]
   (let [bindings (query-term-bindings query-term)]
     (if (empty? bindings)
       query-term
       (bind-term-relations
         relations
         (remove-query-term-bindings query-term)
         bindings))))
  ([relations [ent-type refs attrs query-ref-name generated?] bindings]
   (let [template (get-in relations [ent-type ::template 0])
         bindings (reduce-kv (fn [bindings ref-attr ref-name]
                               (assoc bindings (template ref-attr) ref-name))
                             bindings
                             refs)
         term [(or query-ref-name ent-type)
               (->> (reduce-kv (fn [refs template-ref-attr template-ref-path]
                                 (assoc refs
                                        template-ref-attr
                                        (bind-term-relations relations
                                                             (let [ref-ent-type (first template-ref-path)
                                                                   ref (get bindings template-ref-path)
                                                                   extended-ref? (vector? ref)]
                                                               [ref-ent-type
                                                                (if extended-ref? (second ref) {}) ;; refs
                                                                (if extended-ref? (nth ref 2) {})  ;; attrs
                                                                (cond extended-ref? (first ref)    ;; query-ref-name
                                                                      ref ref
                                                                      :else (keyword (gensym (name ref-ent-type))))
                                                                (not ref) ;; generated?
                                                                ])
                                                             bindings)))
                               refs
                               template)
                    (medley/remove-vals empty-ref?))
               attrs]]
     (if (and (empty-ref? term) (not generated?))
       query-ref-name
       term))))

(defn bind-relations
  "Updates terms to include `binding`"
  [bindings & terms]
  (let [bindings (apply hash-map bindings)]
    (mapv (fn [term]
            (-> (partition query-term-arity query-term-arity (repeat nil) term)
                first
                vec
                (update (dec query-term-arity) #(merge bindings %))))
          terms)))

(defn- gen-format-query
  "Takes a query written in the query DSL and transforms it to a
  format used to generate a map from the query term"
  [relations query]
  (->> (flatten-query relations query)
       (merge-query-refs relations)))

(defn- vectorize-query-terms
  [query]
  (mapv (fn [term] (if (vector? term) term [term])) query))

(defn- format-query
  "Takes a query written in the query DSL and"
  [relations query]
  (->> (vectorize-query-terms query)
       (reduce (fn [xs x]
                 (if (vector? (first x))
                   (into xs x)
                   (conj xs x)))
               [])
       (map #(bind-term-relations relations %))))

(defn- gen-refs
  "Given a tree and refs `{:foo [::bar ::template :id]}`
  returns a map with key `:foo` and value 
  `(get-in tree [::bar ::template :id])`"
  [tree refs]
  (reduce-kv (fn [attrs k path]
               (assoc attrs k (get-in tree path)))
             {}
             refs))

(defn- gen-ent
  [[ent-refs ent-attrs] ent-type gen-fn tree]
  (merge (gen-fn ent-type)
         (gen-refs tree ent-refs)
         ent-attrs))

(defn gen-tree
  "Generates the entire graph necessary for `query` to exist. See
  the README or examples/reifyhealth/specmonstah_examples.clj"
  [gen-fn relations query]
  (let [query (format-query relations query)
        gen-formatted-query (gen-format-query relations query)
        full-relations (add-query-relations relations query)
        sorted-ents (selected-ents full-relations gen-formatted-query)
        tree (reduce (fn [tree ent-path]
                       (let [[ent-type ent-name] ent-path
                             ent (get-in full-relations ent-path)]
                         (assoc-in tree [ent-type ent-name] (gen-ent ent ent-type gen-fn tree))))
                     {} sorted-ents)]
    (assoc tree
           ::query (mapv (fn [[ent-type ent-refs ent-attrs]]
                           [ent-type (gen-ent [ent-refs ent-attrs] ent-type gen-fn tree)])
                         gen-formatted-query)
           ::order sorted-ents)))

(defn dotree
  "Generates the entire graph necessary for `query` to exist, and
  calls `do-fn` on each node of that graph. `do-fn` expects one
  argument, a vector, where the first elemeent is a type (typically a
  spec, like `::book`), and the second element is data generated by
  `gen-tree`."
  [do-fn gen-fn relations query]
  (let [tree (gen-tree gen-fn relations query)]
    (doseq [ent-path (::order tree)]
      (do-fn [(first ent-path) (get-in tree ent-path)]))
    tree))

(defn doall
  "Calls `dotree`, and also calls `do-fn` on each element in the key
  `::query` of the map returned by `gen-tree`"
  [do-fn gen-fn relations query]
  (let [tree (dotree do-fn gen-fn relations query)]
    (doseq [ent (::query tree)] (do-fn ent))
    tree))
