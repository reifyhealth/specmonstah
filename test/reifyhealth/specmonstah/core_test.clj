(ns reifyhealth.specmonstah.core-test
  (:require [reifyhealth.specmonstah.core :as sm]
            [clojure.spec :as s]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [clojure.test.check.generators :as gen :include-macros true]))

(def id-seq (atom 0))

(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(defn test-fixture [f]
  (reset! id-seq 0)
  (f))

(use-fixtures :each test-fixture)

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
  (let [query (sm/vectorize-query-terms query)]
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

(deftest selected-ents
  (let [query [[::book] [::book {:author-id :auth1}]]
        formatted-query (#'sm/gen-format-query template-relations query)]
    (is (= (#'sm/selected-ents (#'sm/add-query-relations template-relations query) formatted-query)
           [[::author ::sm/template]
            [::publisher ::sm/template]
            [::author :auth1]]))))

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
  (is (= (#'sm/gen-tree gen1 template-relations [::book])
         {::author {::sm/template {:id 1 :author-name "Fabrizio S."}}
          ::publisher {::sm/template {:id 2 :publisher-name "PublishCo"}}
          ::sm/query [[::book {:id 3 :book-name "The Book" :author-id 1 :publisher-id 2}]]
          ::sm/order [[::author ::sm/template]
                      [::publisher ::sm/template]]}))

  (is (= (#'sm/gen-tree gen1 template-relations [[::book {} {:book-name "Custom Book Name 1"}]
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
                      [::book :b1]]})))
