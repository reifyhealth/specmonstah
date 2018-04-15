(ns reifyhealth.specmonstah.core-test
  (:require #?(:clj [clojure.test :refer [deftest is are use-fixtures testing]]
               :cljs [cljs.test :include-macros true])
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen :include-macros true]
            [reifyhealth.specmonstah.test-data :as td]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [loom.graph :as lg]
            [loom.alg :as la]
            [loom.attr :as lat]))

(use-fixtures :each td/test-fixture)

(defmacro is-graph=
  "Breaks graph equality test into comparisons on graph keys to
  pinpoint inequality more quickly"
  [g1 g2]
  (let [g1-sym 'returned
        g2-sym 'expected]
    `(let [~g1-sym ~g1
           ~g2-sym ~g2]
       (are [k] (= (k ~g1-sym) (k ~g2-sym))
         :nodeset
         :adj
         :in
         :attrs))))

(deftest test-relation-graph
  (is-graph= (sm/relation-graph td/schema)
             (lg/digraph [:project :todo-list]
                         [:project :user]
                         [:todo-list-watch :todo-list]
                         [:todo-list-watch :user]
                         [:todo :todo-list]
                         [:todo-list :user]
                         [:todo :user]
                         [:attachment :todo]
                         [:attachment :user])))

(defn strip-db
  [db]
  (dissoc db :relation-graph :types :type-order))

(deftest test-build-ent-db-empty
  (is-graph= (strip-db (sm/build-ent-db {:schema td/schema} {}))
             {:schema td/schema
              :data   (lg/digraph)}))

(deftest test-bound-relation-attr-name
  (is (= (sm/bound-relation-attr-name (sm/build-ent-db {:schema td/schema} {}) :tl-bound-p0-0 :todo 0)
         :t-bound-p0-0)))

(deftest test-build-ent-db-relationless-ent
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:user [[:u1]]}))
             (-> (lg/digraph [:user :u1])
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :u1 :type :ent)
                 (lat/add-attr :u1 :index 0)
                 (lat/add-attr :u1 :query-term [:u1])
                 (lat/add-attr :u1 :ent-type :user))))

(deftest test-build-ent-db-mult-relationless-ents
  (is-graph= (:data (strip-db (sm/build-ent-db {:schema td/schema} {:user [3]})))
             (-> (lg/digraph [:user :u0] [:user :u1] [:user :u2])
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :u0 :type :ent)
                 (lat/add-attr :u0 :index 0)
                 (lat/add-attr :u0 :query-term nil)
                 (lat/add-attr :u0 :ent-type :user)
                 (lat/add-attr :u1 :type :ent)
                 (lat/add-attr :u1 :index 1)
                 (lat/add-attr :u1 :query-term nil)
                 (lat/add-attr :u1 :ent-type :user)
                 (lat/add-attr :u2 :type :ent)
                 (lat/add-attr :u2 :index 2)
                 (lat/add-attr :u2 :query-term nil)
                 (lat/add-attr :u2 :ent-type :user))))

(deftest test-build-ent-db-one-level-relation
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:todo-list [1]}))
             (-> (lg/digraph [:user :u0] [:todo-list :tl0] [:tl0 :u0])
                 
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :u0 :type :ent)
                 (lat/add-attr :u0 :index 0)
                 (lat/add-attr :u0 :query-term nil)
                 (lat/add-attr :u0 :ent-type :user)

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term nil)
                 
                 (lat/add-attr :tl0 :u0 :relation-attrs #{:created-by-id :updated-by-id}))))

(deftest test-build-ent-db-mult-ents-w-extended-query
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:todo-list [[2 {:created-by-id :bloop :updated-by-id :bloop}]]}))
             (-> (lg/digraph [:user :bloop]
                             [:todo-list :tl0]
                             [:todo-list :tl1]
                             [:tl0 :bloop]
                             [:tl1 :bloop])
                 
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :bloop :type :ent)
                 (lat/add-attr :bloop :index 0)
                 (lat/add-attr :bloop :query-term nil)
                 (lat/add-attr :bloop :ent-type :user)

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term [2 {:created-by-id :bloop :updated-by-id :bloop}])

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl1 :type :ent)
                 (lat/add-attr :tl1 :index 1)
                 (lat/add-attr :tl1 :ent-type :todo-list)
                 (lat/add-attr :tl1 :query-term [2 {:created-by-id :bloop :updated-by-id :bloop}])

                 (lat/add-attr :tl0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
                 (lat/add-attr :tl1 :bloop :relation-attrs #{:created-by-id :updated-by-id}))))

(deftest test-build-ent-db-one-level-relation-custom-related
  (is-graph= (:data (strip-db (sm/build-ent-db {:schema td/schema} {:todo-list [[:_ {:created-by-id :owner0
                                                                                     :updated-by-id :owner0}]]})))
             (-> (lg/digraph [:user :owner0] [:todo-list :tl0] [:tl0 :owner0])
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :owner0 :type :ent)
                 (lat/add-attr :owner0 :index 0)
                 (lat/add-attr :owner0 :query-term nil)
                 (lat/add-attr :owner0 :ent-type :user)
                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term [:_ {:created-by-id :owner0
                                                    :updated-by-id :owner0}])
                 (lat/add-attr :tl0 :owner0 :relation-attrs #{:updated-by-id :created-by-id}))))

(deftest test-build-ent-db-two-level-coll-relation
  (testing "can specify how many ents to gen in a coll relationship"
    (is-graph= (:data (strip-db (sm/build-ent-db {:schema td/schema} {:project [[:_ {:todo-list-ids 2}]]})))
               (-> (lg/digraph [:user :u0]
                               [:todo-list :tl0] [:todo-list :tl1]  [:tl0 :u0] [:tl1 :u0]
                               [:project :p0] [:p0 :u0] [:p0 :tl0] [:p0 :tl1] [:p0 :u0])

                   (lat/add-attr :user :type :ent-type)
                   (lat/add-attr :u0 :type :ent)
                   (lat/add-attr :u0 :index 0)
                   (lat/add-attr :u0 :query-term nil)
                   (lat/add-attr :u0 :ent-type :user)
                   
                   (lat/add-attr :project :type :ent-type)
                   (lat/add-attr :p0 :type :ent)
                   (lat/add-attr :p0 :index 0)
                   (lat/add-attr :p0 :query-term [:_ {:todo-list-ids 2}])
                   (lat/add-attr :p0 :ent-type :project)
                   (lat/add-attr :p0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   
                   (lat/add-attr :todo-list :type :ent-type)
                   (lat/add-attr :tl0 :type :ent)
                   (lat/add-attr :tl0 :index 0)
                   (lat/add-attr :tl0 :ent-type :todo-list)
                   (lat/add-attr :tl0 :query-term nil)

                   (lat/add-attr :todo-list :type :ent-type)
                   (lat/add-attr :tl1 :type :ent)
                   (lat/add-attr :tl1 :index 1)
                   (lat/add-attr :tl1 :ent-type :todo-list)
                   (lat/add-attr :tl1 :query-term nil)

                   (lat/add-attr :p0 :tl0 :relation-attrs #{:todo-list-ids})
                   (lat/add-attr :p0 :tl1 :relation-attrs #{:todo-list-ids})
                   (lat/add-attr :p0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   (lat/add-attr :tl0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   (lat/add-attr :tl1 :u0 :relation-attrs #{:created-by-id :updated-by-id})))))

(deftest test-build-ent-db-two-level-coll-relation-names
  (testing "can specify names in a coll relationship"
    (is-graph= (:data (strip-db (sm/build-ent-db {:schema td/schema} {:project [[:_ {:todo-list-ids [:mario :luigi]}]]})))
               (-> (lg/digraph [:user :u0]
                               [:todo-list :mario] [:todo-list :luigi]  [:mario :u0] [:luigi :u0]
                               [:project :p0] [:p0 :u0] [:p0 :mario] [:p0 :luigi] [:p0 :u0])

                   (lat/add-attr :user :type :ent-type)
                   (lat/add-attr :u0 :type :ent)
                   (lat/add-attr :u0 :index 0)
                   (lat/add-attr :u0 :query-term nil)
                   (lat/add-attr :u0 :ent-type :user)
                   
                   (lat/add-attr :project :type :ent-type)
                   (lat/add-attr :p0 :type :ent)
                   (lat/add-attr :p0 :index 0)
                   (lat/add-attr :p0 :query-term [:_ {:todo-list-ids [:mario :luigi]}])
                   (lat/add-attr :p0 :ent-type :project)
                   (lat/add-attr :p0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   
                   (lat/add-attr :todo-list :type :ent-type)
                   (lat/add-attr :mario :type :ent)
                   (lat/add-attr :mario :index 0)
                   (lat/add-attr :mario :ent-type :todo-list)
                   (lat/add-attr :mario :query-term nil)

                   (lat/add-attr :todo-list :type :ent-type)
                   (lat/add-attr :luigi :type :ent)
                   (lat/add-attr :luigi :index 1)
                   (lat/add-attr :luigi :ent-type :todo-list)
                   (lat/add-attr :luigi :query-term nil)

                   (lat/add-attr :p0 :mario :relation-attrs #{:todo-list-ids})
                   (lat/add-attr :p0 :luigi :relation-attrs #{:todo-list-ids})
                   (lat/add-attr :p0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   (lat/add-attr :mario :u0 :relation-attrs #{:created-by-id :updated-by-id})
                   (lat/add-attr :luigi :u0 :relation-attrs #{:created-by-id :updated-by-id})))))

(deftest test-build-ent-db-one-level-relation-binding
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:todo-list [[:_ nil {:user :bloop}]]}))
             (-> (lg/digraph [:user :bloop] [:todo-list :tl0] [:tl0 :bloop])
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :bloop :type :ent)
                 (lat/add-attr :bloop :index 0)
                 (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])
                 (lat/add-attr :bloop :ent-type :user)
                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term [:_ nil {:user :bloop}])
                 (lat/add-attr :tl0 :bloop :relation-attrs #{:created-by-id :updated-by-id}))))

(deftest test-build-ent-db-two-level-relation-binding
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:todo [[:_ nil {:user :bloop}]]}))
             (-> (lg/digraph [:user :bloop]
                             [:todo :t0]
                             [:todo-list :tl-bound-t0-0]
                             [:t0 :bloop]
                             [:t0 :tl-bound-t0-0]
                             [:tl-bound-t0-0 :bloop])
                 
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :bloop :type :ent)
                 (lat/add-attr :bloop :index 0)
                 (lat/add-attr :bloop :ent-type :user)
                 (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])

                 (lat/add-attr :todo :type :ent-type)
                 (lat/add-attr :t0 :type :ent)
                 (lat/add-attr :t0 :index 0)
                 (lat/add-attr :t0 :ent-type :todo)
                 (lat/add-attr :t0 :query-term [:_ nil {:user :bloop}])

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl-bound-t0-0 :type :ent)
                 (lat/add-attr :tl-bound-t0-0 :index 0)
                 (lat/add-attr :tl-bound-t0-0 :ent-type :todo-list)
                 (lat/add-attr :tl-bound-t0-0 :query-term [nil nil {:user :bloop}])

                 (lat/add-attr :t0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
                 (lat/add-attr :t0 :tl-bound-t0-0 :relation-attrs #{:todo-list-id})

                 (lat/add-attr :tl-bound-t0-0 :bloop :relation-attrs #{:created-by-id :updated-by-id}))))

(deftest test-build-ent-db-three-level-relation-binding
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:attachment [[:_ nil {:user :bloop}]]}))
             (-> (lg/digraph [:user :bloop]
                             [:attachment :a0]
                             [:todo :t-bound-a0-0]
                             [:todo-list :tl-bound-a0-0]
                             [:a0 :bloop]
                             [:a0 :t-bound-a0-0]
                             [:t-bound-a0-0 :bloop]
                             [:t-bound-a0-0 :tl-bound-a0-0]
                             [:tl-bound-a0-0 :bloop])
                 
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :bloop :type :ent)
                 (lat/add-attr :bloop :index 0)
                 (lat/add-attr :bloop :ent-type :user)
                 (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])

                 (lat/add-attr :todo :type :ent-type)
                 (lat/add-attr :t-bound-a0-0 :type :ent)
                 (lat/add-attr :t-bound-a0-0 :index 0)
                 (lat/add-attr :t-bound-a0-0 :ent-type :todo)
                 (lat/add-attr :t-bound-a0-0 :query-term [nil nil {:user :bloop}])

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl-bound-a0-0 :type :ent)
                 (lat/add-attr :tl-bound-a0-0 :index 0)
                 (lat/add-attr :tl-bound-a0-0 :ent-type :todo-list)
                 (lat/add-attr :tl-bound-a0-0 :query-term [nil nil {:user :bloop}])

                 (lat/add-attr :attachment :type :ent-type)
                 (lat/add-attr :a0 :type :ent)
                 (lat/add-attr :a0 :index 0)
                 (lat/add-attr :a0 :ent-type :attachment)
                 (lat/add-attr :a0 :query-term [:_ nil {:user :bloop}])

                 (lat/add-attr :a0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
                 (lat/add-attr :a0 :t-bound-a0-0 :relation-attrs #{:todo-id})

                 (lat/add-attr :t-bound-a0-0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
                 (lat/add-attr :t-bound-a0-0 :tl-bound-a0-0 :relation-attrs #{:todo-list-id})

                 (lat/add-attr :tl-bound-a0-0 :bloop :relation-attrs #{:created-by-id :updated-by-id}))))
=
(deftest test-build-ent-db-uniq-constraint
  (is-graph= (:data (sm/build-ent-db {:schema td/schema} {:todo-list-watch [2]}))
             (-> (lg/digraph [:user :u0]
                             [:todo-list :tl0]
                             [:tl0 :u0]
                             [:todo-list :tl1]
                             [:tl1 :u0]
                             [:todo-list-watch :tlw0]
                             [:tlw0 :tl0]
                             [:tlw0 :u0]
                             [:todo-list-watch :tlw1]
                             [:tlw1 :tl1]
                             [:tlw1 :u0])
                 (lat/add-attr :user :type :ent-type)
                 (lat/add-attr :u0 :type :ent)
                 (lat/add-attr :u0 :index 0)
                 (lat/add-attr :u0 :ent-type :user)
                 (lat/add-attr :u0 :query-term nil)
                 
                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term nil)

                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl1 :type :ent)
                 (lat/add-attr :tl1 :index 1)
                 (lat/add-attr :tl1 :ent-type :todo-list)
                 (lat/add-attr :tl1 :query-term nil)
                 
                 (lat/add-attr :todo-list-watch :type :ent-type)
                 (lat/add-attr :tlw0 :type :ent)
                 (lat/add-attr :tlw0 :index 0)
                 (lat/add-attr :tlw0 :ent-type :todo-list-watch)
                 (lat/add-attr :tlw0 :query-term nil)

                 (lat/add-attr :todo-list-watch :type :ent-type)
                 (lat/add-attr :tlw1 :type :ent)
                 (lat/add-attr :tlw1 :index 1)
                 (lat/add-attr :tlw1 :ent-type :todo-list-watch)
                 (lat/add-attr :tlw1 :query-term nil)
                 
                 (lat/add-attr :tl0 :u0 :relation-attrs #{:updated-by-id :created-by-id})
                 (lat/add-attr :tl1 :u0 :relation-attrs #{:updated-by-id :created-by-id})

                 (lat/add-attr :tlw0 :tl0 :relation-attrs #{:todo-list-id})
                 (lat/add-attr :tlw0 :u0 :relation-attrs #{:watcher-id})
                 (lat/add-attr :tlw1 :tl1 :relation-attrs #{:todo-list-id})
                 (lat/add-attr :tlw1 :u0 :relation-attrs #{:watcher-id}))))

(deftest test-bound-descendants?
  (is (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :attachment))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :user)))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:attachment :bibbity} :user))))

(deftest queries-can-have-anon-names
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:user [[:_] [:_]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :u0] [:user :u1] )
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :u0 :type :ent)
               (lat/add-attr :u0 :index 0)
               (lat/add-attr :u0 :query-term [:_])
               (lat/add-attr :u0 :ent-type :user)
               (lat/add-attr :u1 :type :ent)
               (lat/add-attr :u1 :index 1)
               (lat/add-attr :u1 :query-term [:_])
               (lat/add-attr :u1 :ent-type :user))))))

(deftest test-build-ent-db-throws-exception-on-invalid-db
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"db is invalid"
                        (sm/build-ent-db {:schema []} {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"query is invalid"
                        (sm/build-ent-db {:schema td/schema} {:user [[]]}))))

(deftest updates-node-attrs
  (let [db (-> (sm/build-ent-db {:schema td/schema} {:user [[:_]]})
               (sm/traverse-ents-add-attr :custom-attr-key (constantly "yaaaaay a key")))]
    (is (= (lat/attr (:data db) :u0 :custom-attr-key)
           "yaaaaay a key"))))

(deftest does-not-override-node-attr
  (testing "If node already has attr, subsequent invocations of traverse-ents-add-attr will not overwrite it"
    (let [db (-> (sm/build-ent-db {:schema td/schema} {:user [[:_]]})
                 (sm/traverse-ents-add-attr :custom-attr-key (constantly nil))
                 (sm/traverse-ents-add-attr :custom-attr-key (constantly "yaaaaay a key")))]
      (is (nil? (lat/attr (:data db) :u0 :custom-attr-key))))))

(deftest assert-schema-refs-must-exist
  (is (thrown-with-msg? java.lang.AssertionError
                        #"Your schema relations reference nonexistent types: "
                        (sm/build-ent-db {:schema {:user {:relations {:u1 [:circle :circle-id]}}}} {}))))

(deftest assert-no-dupe-prefixes
  (is (thrown-with-msg? java.lang.AssertionError
                        #"You have used the same prefix for multiple entity types: "
                        (sm/build-ent-db {:schema {:user  {:prefix :u}
                                                   :user2 {:prefix :u}}} {}))))

(deftest assert-constraints-must-ref-existing-relations
  (is (thrown-with-msg? java.lang.AssertionError
                        #"Schema constraints reference nonexistent relation attrs: "
                        (sm/build-ent-db {:schema {:user  {:prefix :u
                                                           :constraints {:blarb :coll}}}} {}))))

(deftest enforces-coll-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for coll attrs must be a number or vector"
                        (sm/build-ent-db {:schema td/schema} {:project [[:_ {:todo-list-ids :tl0}]]}))))

(deftest enforces-unary-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for unary attrs must be a keyword"
                        (sm/build-ent-db {:schema td/schema} {:attachment [[:_ {:todo-id [:t0 :t1]}]]}))))

