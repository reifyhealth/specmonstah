(ns reifyhealth.specmonstah.core-test
  (:require [reifyhealth.specmonstah.core :as sm]
            [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [clojure.test.check.generators :as gen :include-macros true]
            [clojure.walk :as walk]))

(def id-seq (atom 0))

(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(defn test-fixture [f]
  (reset! id-seq 0)
  (f))

(use-fixtures :each test-fixture)

(defn before? [a b xs]
  (< (.indexOf xs a)
     (.indexOf xs b)))

(defn references? [tree p1 p2]
  (= (get-in tree p1)
     (get-in tree p2)))

(s/def ::id
  (s/with-gen
    pos-int?
    #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))

(s/def ::author-name #{"Fabrizio S."})
(s/def ::author (s/keys :req-un [::id ::author-name]))

(s/def ::book-name #{"The Book"})
(s/def ::book (s/keys :req-un [::id ::book-name]))

(s/def ::chapter-name #{"Chapter 1"})
(s/def ::chapter (s/keys :req-un [::id ::chapter-name]))

(s/def ::publisher-name #{"PublishCo"})
(s/def ::publisher (s/keys :req-un [::id ::publisher-name]))

(def relation-template
  {::author []
   ::publisher []
   ::book [{:author-id [::author :id]
            :publisher-id [::publisher :id]}]
   ::chapter [{:book-id [::book :id]}]})

(def template-relations (sm/expand-relation-template relation-template))

(defn normalize-and-expand
  [query]
  (let [query (#'sm/vectorize-query-terms query)]
    [(#'sm/gen-format-query template-relations query)
     (#'sm/add-query-relations template-relations query)]))

(deftest gen-format-query
  (is (= (#'sm/gen-format-query template-relations [[::book]])
         [[::book
           {:author-id [::author ::sm/template :id]
            :publisher-id [::publisher ::sm/template :id]}
           nil]]))
  (is (= (#'sm/gen-format-query template-relations [[::book] [::book {:author-id :new}]])
         [[::book
           {:author-id [::author ::sm/template :id]
            :publisher-id [::publisher ::sm/template :id]}
           nil]
          [::book
           {:author-id [::author :new :id]
            :publisher-id [::publisher ::sm/template :id]}
           nil]])))

(deftest add-query-relations
  (is (= (#'sm/add-query-relations template-relations [[::book] [::book {:author-id :auth1}]])
         {::author {::sm/template [nil nil] :auth1 [nil nil]}
          ::publisher {::sm/template [nil nil]}
          ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                  :publisher-id [::publisher ::sm/template :id]}
                                 nil]}
          ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}}))

  (is (= (#'sm/add-query-relations template-relations [[::book {:author-id :a1 :publisher-id :p1}]])
         {::author {::sm/template [nil nil] :a1 [nil nil]}
          ::publisher {::sm/template [nil nil] :p1 [nil nil]}
          ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                  :publisher-id [::publisher ::sm/template :id]}
                                 nil]} 
          ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}}))

  (testing "When you name a new ref, its refs get copied from the template"
    (is (= (#'sm/add-query-relations template-relations [[::chapter {:book-id :b1}]])
           {::author {::sm/template [nil nil]}
            ::publisher {::sm/template [nil nil]}
            ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                    :publisher-id [::publisher ::sm/template :id]}
                                   nil]
                    :b1 [{:author-id [::author ::sm/template :id]
                          :publisher-id [::publisher ::sm/template :id]}
                         nil]} 
            ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}})))

  (testing "You can recursively name refs"
    (is (= (#'sm/add-query-relations template-relations [[::chapter {:book-id [:b1 {:author-id :a1}]}]])
           {::author {::sm/template [nil nil] :a1 [nil nil]}
            ::publisher {::sm/template [nil nil]}
            ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                    :publisher-id [::publisher ::sm/template :id]}
                                   nil]
                    :b1 [{:author-id [::author :a1 :id]
                          :publisher-id [::publisher ::sm/template :id]}
                         nil]} 
            ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}})))

  (testing "You can specify attrs for refs"
    (is (= (#'sm/add-query-relations template-relations [[::book {:author-id [:auth1 {} {:author-name "Fred"}]}]])
           {::author {::sm/template [nil nil]
                      :auth1 [{} {:author-name "Fred"}]}
            ::publisher {::sm/template [nil nil]}
            ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                    :publisher-id [::publisher ::sm/template :id]}
                                   nil]}
            ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}}))))

(deftest ent-references
  (is (= (#'sm/ent-references nil)
         #{}))
  (is (= (#'sm/ent-references {:author-id [::author ::sm/template :id]
                               :publisher-id [::publisher ::sm/template :id]})
         #{[::author ::sm/template]
           [::publisher ::sm/template]}))
  (is (= (#'sm/ent-references {:author-id [::author ::auth1 :id]
                               :publisher-id [::publisher ::sm/template :id]})
         #{[::author ::auth1]
           [::publisher ::sm/template]})))

(deftest references
  (is (= (#'sm/references (#'sm/gen-format-query template-relations [[::book] [::book {:author-id :auth1}]]))
         #{[::author :auth1]
           [::author ::sm/template]
           [::publisher ::sm/template]})))

(deftest topo
  (is (= (#'sm/topo (#'sm/add-query-relations template-relations [[::book] [::book {:author-id :auth1}]]))
         {[::author ::sm/template] #{}
          [::author :auth1] #{}
          [::publisher ::sm/template] #{}
          [::book ::sm/template] #{[::author ::sm/template] [::publisher ::sm/template]}
          [::chapter ::sm/template] #{[::book ::sm/template]}})))

;; TODO update

(deftest selected-ents
  (let [query [[::chapter] [::chapter {:book-id [:book1 {:author-id :auth1}]}]]
        formatted-query (#'sm/gen-format-query template-relations query)
        selected (#'sm/selected-ents (#'sm/add-query-relations template-relations query) formatted-query)]
    (is (before? [::author :auth1] [::book :book1] selected))
    (is (before? [::author ::sm/template] [::book ::sm/template] selected))
    (is (before? [::publisher ::sm/template][::book ::sm/template] selected))))


(deftest flatten-query
  (is (= (#'sm/flatten-query template-relations [[::book] [::chapter {:book-id [:b1 {:author-id :a1}]}]])
         [[::book nil nil] [::chapter {:book-id :b1} nil]])))

(deftest merge-query-refs
  (is (= (#'sm/merge-query-refs template-relations
                                [[::book {}] [::chapter {:book-id :b1}]])
         [[::book
           {:author-id [::author ::sm/template :id]
            :publisher-id [::publisher ::sm/template :id]}
           nil]
          [::chapter
           {:book-id [::book :b1 :id]}
           nil]])))

(deftest add-query-term-relations
  (is (= (#'sm/add-query-term-relations [::chapter {:book-id [:b1 {:author-id :a1}]}] template-relations)
         {::author {::sm/template [nil nil]
                    :a1 [nil nil]}
          ::publisher {::sm/template [nil nil]}
          ::book {::sm/template [{:author-id [::author ::sm/template :id]
                                  :publisher-id [::publisher ::sm/template :id]}
                                 nil]
                  :b1 [{:author-id [::author :a1 :id]
                        :publisher-id [::publisher ::sm/template :id]}
                       nil]}
          ::chapter {::sm/template [{:book-id [::book ::sm/template :id]} nil]}})))

(deftest gen-tree
  (let [tree (sm/gen-tree gen1 template-relations [::book])]
    ;; Remove order because it's nondeterministic for nodes that are
    ;; topographically on the same level
    (is (= (dissoc tree ::sm/order)
           {::author {::sm/template {:id 1 :author-name "Fabrizio S."}}
            ::publisher {::sm/template {:id 2 :publisher-name "PublishCo"}}
            ::sm/query [[::book {:id 3 :book-name "The Book" :author-id 1 :publisher-id 2}]]}))
    (is (= (set (::sm/order tree))
           #{[::author ::sm/template]
             [::publisher ::sm/template]})))

  (is (= (sm/gen-tree gen1 template-relations [[::book {} {:book-name "Custom Book Name 1"}]
                                               [::chapter {:book-id [:b1 {:author-id :a1} {:book-name "Nested Query Book Name"}]}]])
         {::author {:a1 {:id 6 :author-name "Fabrizio S."}
                    ::sm/template {:id 4 :author-name "Fabrizio S."}}
          ::publisher {::sm/template {:id 5 :publisher-name "PublishCo"}}
          ::book {:b1 {:id 7 :book-name "Nested Query Book Name" :author-id 6 :publisher-id 5}}
          ::sm/query [[::book {:id 8 :book-name "Custom Book Name 1" :author-id 4 :publisher-id 5}]
                      [::chapter {:id 9 :chapter-name "Chapter 1" :book-id 7}]]
          ::sm/order [[::author ::sm/template]
                      [::publisher ::sm/template]
                      [::author :a1]
                      [::book :b1]]}))

  ;; Test that nested ref attributes get merged. :book-name and
  ;; :author-id are added in separate refs, but the result has them
  ;; merged.
  (is (= (sm/gen-tree gen1 template-relations [[::chapter {:book-id [:b1 {} {:book-name "Nested Query Book Name"}]}]
                                               [::chapter {:book-id [:b1 {} {:author-id "Custom Author Id"}]}]
                                               [::chapter {:book-id [:b1 {} {}]}]])
         {::author {::sm/template {:id 10 :author-name "Fabrizio S."}}
          ::publisher {::sm/template {:id 11 :publisher-name "PublishCo"}}
          ::book {:b1 {:id 12 :book-name "Nested Query Book Name" :author-id "Custom Author Id" :publisher-id 11}}
          ::sm/query [[::chapter {:id 13 :chapter-name "Chapter 1" :book-id 12}]
                      [::chapter {:id 14 :chapter-name "Chapter 1" :book-id 12}]
                      [::chapter {:id 15 :chapter-name "Chapter 1" :book-id 12}]]
          ::sm/order [[::author ::sm/template]
                      [::publisher ::sm/template]
                      [::book :b1]]})))

;; ---------
;; Test binding
;; ---------

(s/def ::site-tag-group (s/keys :req-un [::id]))

(s/def ::site-name #{"Site"})
(s/def ::site (s/keys :req-un [::id ::site-name]))

(s/def ::site-foo (s/keys :req-un [::id]))

(s/def ::site-tag-name #{"Taggity"})
(s/def ::site-foo-id ::id)
(s/def ::site-tag (s/keys :req-un [::id ::site-tag-name ::site-foo-id]))

(s/def ::site-user-name #{"Flamantha"})
(s/def ::site-user-tag-id ::id)
(s/def ::site-user (s/keys :req-un [::id ::site-user-name ::site-user-tag-id]))

(def binding-relation-template
  {::site-tag-group []
   ::site []
   ::site-tag [{:site-id [::site :id]
                :site-tag-group-id [::site-tag-group :id]
                :site-foo-id [::site-foo :id]}]
   ::site-user [{:site-id [::site :id]
                 :site-user-tag-id [::site-tag :id]}]})

(def binding-template-relations (sm/expand-relation-template binding-relation-template))
(defn rename-generated-keys
  "Renames random name generated by spec to something deterministic"
  [key-prefixes xs]
  (let [counter (atom (into {} (map (fn [prefix] [prefix 0]) key-prefixes)))
        key-prefixes (set key-prefixes)
        matches? (fn [kw]
                   (some (fn [prefix]
                           (and (re-matches (re-pattern (str "^" prefix "\\d+")) (str kw))
                                prefix))
                         key-prefixes))]
    (walk/postwalk (fn [x]
                     (if-let [prefix (and (keyword? x) (matches? x))]
                       (if (x @counter)
                         (x @counter)
                         (let [new-name (keyword (str (name prefix) "-" (prefix @counter)))]
                           (swap! counter (fn [c]
                                            (-> (update c prefix inc)
                                                (assoc x new-name))))
                           new-name))
                       x))
                   xs)))

(deftest bind-term-relations
  (testing "binds entire tree when ref key is a vector"
    ;; test helper renames :site-tag434154 to :site-tag-0
    (is (= (->> (#'sm/bind-term-relations binding-template-relations [::site-user {:site-id :s1
                                                                                   [::site-foo :id] :sf1}])
                (rename-generated-keys [:site-tag]))
           [::site-user {:site-id :s1
                         :site-user-tag-id [:site-tag-0 {:site-id :s1
                                                         :site-foo-id :sf1} {}]} nil]))
    (is (= (->> (#'sm/bind-term-relations binding-template-relations [::site-user {[::site-tag :id] :st1
                                                                                   [::site-foo :id] :sf1}])
                (rename-generated-keys [:site-tag]))
           [::site-user {:site-user-tag-id [:st1 {:site-foo-id :sf1} {}]} nil]))))

(deftest gen-tree-with-bindings
  (testing "generates tree with bindings"
    ;; test helper renames :site-tag434154 to :site-tag-0
    (is (= (->> (sm/gen-tree gen1 binding-template-relations [[::site-user {[::site-foo :id] :sf1}]])
                (rename-generated-keys [:site-tag]))
           {::site-tag-group {::sm/template {:id 1}}
            ::site {::sm/template {:id 2 :site-name "Site"}}
            ::site-foo {:sf1 {:id 3}}
            ::site-tag {:site-tag-0 {:id 4
                                     :site-tag-name "Taggity"
                                     :site-foo-id 3
                                     :site-id 2
                                     :site-tag-group-id 1}}
            ::sm/query [[::site-user {:id 6,
                                      :site-user-name "Flamantha"
                                      :site-user-tag-id 4
                                      :site-id 2}]]
            ::sm/order [[::site-tag-group ::sm/template]
                        [::site ::sm/template]
                        [::site-foo :sf1]
                        [::site-tag :site-tag-0]]}))))

(deftest gen-tree-with-bind-relations-helper
  (testing "bind-relations helper"
    (is (= (->> (sm/gen-tree
                  gen1
                  binding-template-relations
                  [(sm/bind-relations [[::site-tag :id] :st1
                                       [::site-foo :id] :sf1]
                     [::site-user]
                     [::site-user])]))
           {::site-tag-group {::sm/template {:id 1}}
            ::site {::sm/template {:id 2, :site-name "Site"}},
            ::site-foo {:sf1 {:id 3}},
            ::site-tag {:st1 {:id 4
                              :site-tag-name "Taggity"
                              :site-foo-id 3
                              :site-tag-group-id 1
                              :site-id 2}}
            ::sm/query [[::site-user {:id 6,
                                      :site-user-name "Flamantha",
                                      :site-user-tag-id 4,
                                      :site-id 2}]
                        [::site-user {:id 8,
                                      :site-user-name "Flamantha",
                                      :site-user-tag-id 4,
                                      :site-id 2}]],
            ::sm/order [[::site-tag-group ::sm/template]
                        [::site ::sm/template]
                        [::site-foo :sf1]
                        [::site-tag :st1]]}))))

(deftest gen-tree-with-bind-relations-nested
  (testing "bind-relations helper merges the binding with user-defined relations"
    (let [tree (->> (sm/gen-tree
                      gen1
                      binding-template-relations
                      [(sm/bind-relations [[::site-tag-group :id] :stg1]
                         [::site-user {:site-user-tag-id [:st1 {:site-foo-id :sf1}]}])])
                    (rename-generated-keys [:site-tag]))
          order (::sm/order tree)]
      (is (references? tree [::site-tag :st1 :site-foo-id]       [::site-foo :sf1 :id]))
      (is (references? tree [::site-tag :st1 :site-id]           [::site ::sm/template :id]))
      (is (references? tree [::site-tag :st1 :site-tag-group-id] [::site-tag-group :stg1 :id]))
      (is (references? tree [::sm/query 0 1 :site-user-tag-id]   [::site-tag :st1 :id]))

      (is (= (set order)
             #{[::site ::sm/template]
               [::site-foo :sf1]
               [::site-tag-group :stg1]
               [::site-tag :st1]}))
      (is (before? [::site-tag-group :stg1] [::site-tag :st1] order))
      (is (before? [::site ::sm/template] [::site-foo :sf1] order)))))




;; ---------
;; Test nonexistent relation handling
;; ---------

(deftest handles-nonexistent-relation
  (is (thrown-with-msg?
        #?(:clj clojure.lang.ExceptionInfo
           :cljs cljs.core.ExceptionInfo)
        #"The relation :.*? for :.*? does not exist"
        (sm/gen-tree gen1 template-relations [[::chapter {:nonexistent-id :n1}]]))))

(def default-attr-relation-template
  {::author [{} {:author-name "default"}]
   ::book [{:author-id [::author :id]}]})

(def default-attr-relations (sm/expand-relation-template default-attr-relation-template))

(deftest uses-default-attrs
  (is (= (sm/gen-tree gen1 default-attr-relations [::book])
         {::author {::sm/template {:id 1 :author-name "default"}}
          ::sm/query [[::book {:id 2 :book-name "The Book" :author-id 1}]]
          ::sm/order [[::author ::sm/template]]}))

  (is (= (sm/gen-tree gen1 default-attr-relations [[::book {:author-id [:a1 {} {:id 10}]}]])
         {::author {:a1 {:id 10 :author-name "default"}}
          ::sm/query [[::book {:id 4 :book-name "The Book" :author-id 10}]]
          ::sm/order [[::author :a1]]})))
