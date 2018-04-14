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
                         [:todo-list-watcher :todo-list]
                         [:todo-list-watcher :user]
                         [:todo :todo-list]
                         [:todo-list :user]
                         [:todo :user])))

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

#_(deftest test-build-ent-db-two-level-coll-relation
  (testing "can specify how many ents to gen in a coll relationship"
    (is (= (:data (strip-db (sm/build-ent-db {:schema td/schema} {:todo-list [[:_ {:todo-ids 2}]]})))
           (-> (lg/digraph [:user :u0]
                           [:todo :t0] [:todo :t1] [:t0 :u0] [:t1 :u0]
                           [:todo-list :tl0] [:tl0 :t0] [:tl0 :t1] [:tl0 :u0])

               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :u0 :type :ent)
               (lat/add-attr :u0 :index 0)
               (lat/add-attr :u0 :query-term nil)
               (lat/add-attr :u0 :ent-type :user)
               
               (lat/add-attr :todo :type :ent-type)
               (lat/add-attr :t0 :type :ent)
               (lat/add-attr :t0 :index 0)
               (lat/add-attr :t0 :query-term nil)
               (lat/add-attr :t0 :ent-type :todo)
               (lat/add-attr :t0 :u0 :relation-attrs #{:created-by-id :updated-by-id})
               
               (lat/add-attr :t1 :type :ent)
               (lat/add-attr :t1 :index 1)
               (lat/add-attr :t1 :query-term nil)
               (lat/add-attr :t1 :ent-type :todo)
               (lat/add-attr :t1 :u0 :relation-attrs #{:created-by-id :updated-by-id})
               
               (lat/add-attr :todo-list :type :ent-type)
               (lat/add-attr :tl0 :type :ent)
               (lat/add-attr :tl0 :index 0)
               (lat/add-attr :tl0 :ent-type :todo-list)
               (lat/add-attr :tl0 :query-term [:_ {:todo-ids 2}])
               (lat/add-attr :tl0 :t0 :relation-attrs #{:todo-ids})
               (lat/add-attr :tl0 :t1 :relation-attrs #{:todo-ids})
               (lat/add-attr :tl0 :u0 :relation-attrs #{:created-by-id :updated-by-id}))))))

#_(deftest test-build-ent-db-two-level-has-many-relation
  (testing "can specify ent names in a has-many relationship"
    (is (= (:data (sm/build-ent-db {:schema td/schema} {:todo-list [[:_ {:todo-ids [:my-todo :my-todo-2]}]]}))
           (-> (lg/digraph [:user :u0]
                           [:todo :my-todo] [:todo :my-todo-2] [:my-todo :u0] [:my-todo-2 :u0]
                           [:todo-list :tl0] [:tl0 :my-todo] [:tl0 :my-todo-2] [:tl0 :u0])

               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :u0 :type :ent)
               (lat/add-attr :u0 :index 0)
               (lat/add-attr :u0 :query-term nil)
               (lat/add-attr :u0 :ent-type :user)
               
               (lat/add-attr :todo :type :ent-type)
               (lat/add-attr :my-todo :type :ent)
               (lat/add-attr :my-todo :index 0)
               (lat/add-attr :my-todo :query-term nil)
               (lat/add-attr :my-todo :ent-type :todo)
               (lat/add-attr :my-todo :u0 :relation-attrs #{:created-by-id :updated-by-id})
               
               (lat/add-attr :my-todo-2 :type :ent)
               (lat/add-attr :my-todo-2 :index 1)
               (lat/add-attr :my-todo-2 :query-term nil)
               (lat/add-attr :my-todo-2 :ent-type :todo)
               (lat/add-attr :my-todo-2 :u0 :relation-attrs #{:created-by-id :updated-by-id})
               
               (lat/add-attr :todo-list :type :ent-type)
               (lat/add-attr :tl0 :type :ent)
               (lat/add-attr :tl0 :index 0)
               (lat/add-attr :tl0 :ent-type :todo-list)
               (lat/add-attr :tl0 :query-term [:_ {:todo-ids [:my-todo :my-todo-2]}])
               (lat/add-attr :tl0 :my-todo :relation-attrs #{:todo-ids})
               (lat/add-attr :tl0 :my-todo-2 :relation-attrs #{:todo-ids})
               (lat/add-attr :tl0 :u0 :relation-attrs #{:created-by-id :updated-by-id}))))))

#_(deftest test-build-ent-db-three-level-relation-binding
  (is (= (:data (sm/build-ent-db {:schema td/schema} {:project [[:_ nil {:user :bloop}]]}))
         (-> (lg/digraph [:user :bloop]
                         [:todo :t-bound-p0-0]
                         [:todo-list :tl-bound-p0-0]
                         [:project :p0]
                         [:t-bound-p0-0 :bloop]
                         [:tl-bound-p0-0 :bloop]
                         [:tl-bound-p0-0 :t-bound-p0-0]
                         [:p0 :bloop]
                         [:p0 :tl-bound-p0-0])
             
             (lat/add-attr :user :type :ent-type)
             (lat/add-attr :bloop :type :ent)
             (lat/add-attr :bloop :index 0)
             (lat/add-attr :bloop :ent-type :user)
             (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])

             (lat/add-attr :todo :type :ent-type)
             (lat/add-attr :t-bound-p0-0 :type :ent)
             (lat/add-attr :t-bound-p0-0 :index 0)
             (lat/add-attr :t-bound-p0-0 :ent-type :todo)
             (lat/add-attr :t-bound-p0-0 :query-term [nil nil {:user :bloop}])

             (lat/add-attr :todo-list :type :ent-type)
             (lat/add-attr :tl-bound-p0-0 :type :ent)
             (lat/add-attr :tl-bound-p0-0 :index 0)
             (lat/add-attr :tl-bound-p0-0 :ent-type :todo-list)
             (lat/add-attr :tl-bound-p0-0 :query-term [nil nil {:user :bloop}])

             (lat/add-attr :project :type :ent-type)
             (lat/add-attr :p0 :type :ent)
             (lat/add-attr :p0 :index 0)
             (lat/add-attr :p0 :ent-type :project)
             (lat/add-attr :p0 :query-term [:_ nil {:user :bloop}])

             (lat/add-attr :t-bound-p0-0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
             
             (lat/add-attr :tl-bound-p0-0 :bloop :relation-attrs #{:created-by-id :updated-by-id})
             (lat/add-attr :tl-bound-p0-0 :t-bound-p0-0 :relation-attrs #{:todo-ids})
             
             (lat/add-attr :p0 :tl-bound-p0-0 :relation-attrs #{:todo-list-ids})
             (lat/add-attr :p0 :bloop :relation-attrs #{:created-by-id :updated-by-id})))))

#_(deftest test-build-ent-db-uniq-constraint
  (is (= (:data (sm/build-ent-db {:schema td/schema} {:project-supporter [2]}))
         (-> (lg/digraph [:user :u0]
                         [:project :p0]
                         [:supporter :s0]
                         [:supporter :s1]
                         [:project-supporter :ps0]
                         [:ps0 :s0]
                         [:ps0 :p0]
                         [:ps0 :u0]
                         [:project-supporter :ps1]
                         [:ps1 :s1]
                         [:ps1 :p0]
                         [:ps1 :u0]
                         [:p0 :u0]
                         [:s0 :u0]
                         [:s1 :u0])
             (lat/add-attr :user :type :ent-type)
             (lat/add-attr :u0 :type :ent)
             (lat/add-attr :u0 :index 0)
             (lat/add-attr :u0 :ent-type :user)
             (lat/add-attr :u0 :query-term nil)
             (lat/add-attr :project :type :ent-type)
             (lat/add-attr :p0 :type :ent)
             (lat/add-attr :p0 :index 0)
             (lat/add-attr :p0 :ent-type :project)
             (lat/add-attr :p0 :query-term nil)
             (lat/add-attr :supporter :type :ent-type)
             (lat/add-attr :s0 :type :ent)
             (lat/add-attr :s0 :index 0)
             (lat/add-attr :s0 :ent-type :supporter)
             (lat/add-attr :s0 :query-term nil)
             ;; creates second supporter because that's uniq
             (lat/add-attr :supporter :type :ent-type)
             (lat/add-attr :s1 :type :ent)
             (lat/add-attr :s1 :index 1)
             (lat/add-attr :s1 :ent-type :supporter)
             (lat/add-attr :s1 :query-term nil)
             (lat/add-attr :project-supporter :type :ent-type)

             (lat/add-attr :ps0 :type :ent)
             (lat/add-attr :ps0 :index 0)
             (lat/add-attr :ps0 :ent-type :project-supporter)
             (lat/add-attr :ps0 :query-term nil)
             (lat/add-attr :ps0 :u0 :relation-attrs #{:owner-id})
             (lat/add-attr :ps0 :p0 :relation-attrs #{:project-id})
             (lat/add-attr :ps0 :s0 :relation-attrs #{:supporter-id})

             (lat/add-attr :ps1 :type :ent)
             (lat/add-attr :ps1 :index 1)
             (lat/add-attr :ps1 :ent-type :project-supporter)
             (lat/add-attr :ps1 :query-term nil)
             (lat/add-attr :ps1 :u0 :relation-attrs #{:owner-id})
             (lat/add-attr :ps1 :p0 :relation-attrs #{:project-id})
             (lat/add-attr :ps1 :s1 :relation-attrs #{:supporter-id})
             
             (lat/add-attr :s0 :u0 :relation-attrs #{:updated-by-id :owner-id})
             (lat/add-attr :s1 :u0 :relation-attrs #{:updated-by-id :owner-id})
             (lat/add-attr :p0 :u0 :relation-attrs #{:updated-by-id :owner-id})))))

#_(deftest test-bound-descendants?
  (is (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :ps-list))
  (is (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :project-supporter))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :user)))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:project :bibbity} :user))))

#_(deftest queries-can-have-anon-names
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

#_(deftest test-build-ent-db-throws-exception-on-invalid-db
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"db is invalid"
                        (sm/build-ent-db {:schema []} {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"query is invalid"
                        (sm/build-ent-db {:schema td/schema} {:user [[]]}))))

#_(deftest updates-node-attrs
  (let [db (-> (sm/build-ent-db {:schema td/schema} {:user [[:_]]})
               (sm/traverse-ents-add-attr :custom-attr-key (constantly "yaaaaay a key")))]
    (is (= (lat/attr (:data db) :u0 :custom-attr-key)
           "yaaaaay a key"))))

#_(deftest does-not-override-node-attr
  (let [db (-> (sm/build-ent-db {:schema td/schema} {:user [[:_]]})
               (sm/traverse-ents-add-attr :custom-attr-key (constantly nil))
               (sm/traverse-ents-add-attr :custom-attr-key (constantly "yaaaaay a key")))]
    (is (nil? (lat/attr (:data db) :u0 :custom-attr-key)))))

#_(deftest assert-schema-refs-must-exist
  (is (thrown-with-msg? java.lang.AssertionError
                        #"Your schema relations reference nonexistent types: "
                        (sm/build-ent-db {:schema {:user {:relations {:u1 [:circle :circle-id]}}}} {}))))

#_(deftest assert-no-dupe-prefixes
  (is (thrown-with-msg? java.lang.AssertionError
                        #"You have used the same prefix for multiple entity types: "
                        (sm/build-ent-db {:schema {:user  {:prefix :u}
                                                   :user2 {:prefix :u}}} {}))))

#_(deftest enforces-has-many-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for has-many attrs must be a number or vector"
                        (sm/build-ent-db {:schema td/schema} {:ps-list [[:_ {:ps-ids :ps1}]]}))))

#_(deftest enforces-has-one-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for has-one attrs must be a keyword"
                        (sm/build-ent-db {:schema td/schema} {:project [[:_ {:owner-id [:u1 :u2]}]]}))))

