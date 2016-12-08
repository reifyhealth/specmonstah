(ns specmonsta.core
  (:require [loom.graph :as g]
            [loom.alg :as la]
            [medley.core :as medley]))

#_(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(defn expand-default-refs
  [default-refs]
  (medley/map-vals (fn [v] (if (vector? v)
                            (let [[relation-type field] v]
                              [relation-type ::template field])
                            v))
                   default-refs))

(defn expand-relation-template
  [relation-template]
  (reduce-kv (fn [result relation-type [default-refs default-attrs]]
               (assoc result relation-type {::template [(expand-default-refs default-refs) default-attrs]}))
             {}
             relation-template))

(defn ent-references
  [refs]
  (->> refs
       vals
       (map #(vec (take 2 %)))
       set))

(defn topo
  "takes named relations to produce a DAG of dependencies"
  [relations]
  (reduce-kv (fn [graph ent-type ents]
               (reduce-kv (fn [graph ent-name ent]
                            (assoc graph [ent-type ent-name] (ent-references (first ent))))
                          graph
                          ents))
             {}
             relations))

(defn references
  "Pull references from default ent, accessed by [relation-type
  relation-name], merged with custom attributes from query. Expects a
  gen-formatted query."
  [query]
  (reduce (fn [refs term] (into refs (ent-references (second term))))
          #{}
          query))

(defn selected-ents
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

(defn merge-template-refs
  "(merge-template-refs {:author-id [::author ::template :id]} {:author-id :a1})
   => {:author-id [::author :a1 :id]}"
  [template refs]
  (merge-with (fn [template-relation new-relation-name]
                (assoc template-relation 1 new-relation-name))
              template
              refs))

(defn add-query-term-relations
  "Unpacks the query DSL, inserting referenced entities. For example,
  if the query includes the term
  
  [::book {:author-id :a1}]
  
  Then this function will insert a copy of 
  (get-in relations [::author ::template]) at [::author :a1]."
  [[ent-type refs] relations]
  (reduce-kv (fn [relations ent-field ref-name]
               (let [field-ref-type (get-in relations [ent-type ::template 0 ent-field 0])
                     ref-template (get-in relations [field-ref-type ::template])]
                 (if (vector? ref-name)
                   ;; ref recursively queries its own refs
                   (let [[ref-name ref-refs ref-attrs] ref-name]
                     (add-query-term-relations [field-ref-type ref-refs]
                                               (assoc-in relations
                                                         [field-ref-type ref-name]
                                                         [(merge-template-refs (first ref-template) (medley/map-vals (fn [n] (if (vector? n) (first n) n)) ref-refs))
                                                          (merge (second ref-template) ref-attrs)])))
                   (assoc-in relations [field-ref-type ref-name] ref-template))))
             relations
             refs))

(defn add-query-relations
  "Takes a raw query and adds all the entities referenced"
  [relations query]
  (reduce (fn [relations query-term] (add-query-term-relations query-term relations))
          relations
          query))

(defn flatten-query
  "Ensures that every query term is of the form `[spec refs]`,
  where `refs` is a map.

  Also ensures that `refs` is only one level deep, so
  [[::chapter {:book-id [:b1 {:author-id :a1}]}]] becomes
  [[::chapter {:book-id :b1}]]"
  [relations query]
  (mapv (fn [term]
          (let [[ent-type refs attrs] term]
            [ent-type
             (medley/map-vals (fn [x] (if (vector? x) (first x) x)) refs)
             attrs]))
        query))

(defn merge-query-refs
  "Merges a query term's custom refs with that entity's template"
  [relations query]
  (mapv (fn [[ent-type refs attrs]]
          [ent-type
           (let [template-refs (get-in relations [ent-type ::template 0])]
             (merge-template-refs template-refs refs))
           attrs])
        query))

(defn gen-format-query
  "Takes a query written in the query DSL and transforms it to a
  format used to generate a map from the query term"
  [relations query]
  (->> (flatten-query relations query)
       (merge-query-refs relations)))

(defn vectorize-query-terms
  [query]
  (mapv (fn [term] (if (vector? term) term [term])) query))

(defn gen-refs
  "Given a tree and refs `{:foo [::bar ::template :id]}`
  returns a map with key `:foo` and value 
  `(get-in tree [::bar ::template :id])`"
  [tree refs]
  (reduce-kv (fn [attrs k path]
               (assoc attrs k (get-in tree path)))
             {}
             refs))

(defn gen-ent
  [[ent-refs ent-attrs] ent-type gen-fn tree]
  (merge (gen-fn ent-type)
         (gen-refs tree ent-refs)
         ent-attrs))

(defn gen-tree
  [gen-fn relations query]
  (let [query (vectorize-query-terms query)
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
