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

(deftest test-relation-graph
  (is (= (sm/relation-graph td/schema)
         (lg/digraph [:ps-list :project-supporter]
                     [:project-supporter :project]
                     [:project-supporter :supporter]
                     [:project-supporter :user]
                     [:project :user]
                     [:supporter :user]
                     [:todo-list :todo]))))

(deftest test-bound-descendants?
  (is (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :ps-list))
  (is (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :project-supporter))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:user :bibbity} :user)))
  (is (not (sm/bound-descendants? (sm/init-db {:schema td/schema}) {:project :bibbity} :user))))

(defn strip-db
  [db]
  (dissoc db :relation-graph :types :type-order))

(deftest test-build-ent-db-empty
  (is (= (strip-db (sm/build-ent-db {:schema td/schema} {}))
         {:schema td/schema
          :data   (lg/digraph)})))

(deftest test-build-ent-db-relationless-ent
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:user [[:u1]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :u1])
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :u1 :type :ent)
               (lat/add-attr :u1 :index 0)
               (lat/add-attr :u1 :query-term [:u1])
               (lat/add-attr :u1 :ent-type :user))))))

(deftest test-build-ent-db-mult-relationless-ents
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:user [3]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
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
               (lat/add-attr :u2 :ent-type :user))))))

(deftest test-build-ent-db-one-level-relation
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project [[:p1]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :u0] [:project :p1] [:p1 :u0])
               
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :u0 :type :ent)
               (lat/add-attr :u0 :index 0)
               (lat/add-attr :u0 :query-term nil)
               (lat/add-attr :u0 :ent-type :user)

               (lat/add-attr :project :type :ent-type)
               (lat/add-attr :p1 :type :ent)
               (lat/add-attr :p1 :index 0)
               (lat/add-attr :p1 :ent-type :project)
               (lat/add-attr :p1 :query-term [:p1])
               
               (lat/add-attr :p1 :u0 :relation-attrs #{:updated-by-id :owner-id}))))))

(deftest test-build-ent-db-one-level-relation-binding
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project [[:p1 nil {:user :bloop}]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :bloop] [:project :p1] [:p1 :bloop])
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :bloop :type :ent)
               (lat/add-attr :bloop :index 0)
               (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])
               (lat/add-attr :bloop :ent-type :user)
               (lat/add-attr :project :type :ent-type)
               (lat/add-attr :p1 :type :ent)
               (lat/add-attr :p1 :index 0)
               (lat/add-attr :p1 :ent-type :project)
               (lat/add-attr :p1 :query-term [:p1 nil {:user :bloop}])
               (lat/add-attr :p1 :bloop :relation-attrs #{:updated-by-id :owner-id}))))))

(deftest test-build-ent-db-one-level-has-many-relation
  (testing "can specify how many ents to gen in a has-many relationship"
    (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:todo-list [[:tl0 {:todo-id 2}]]}))]
      (is (= (:schema db) td/schema))
      (is (= (:data db)
             (-> (lg/digraph [:todo :t0] [:todo :t1] [:todo-list :tl0] [:tl0 :t0] [:tl0 :t1])
                 (lat/add-attr :todo :type :ent-type)
                 (lat/add-attr :t0 :type :ent)
                 (lat/add-attr :t0 :index 0)
                 (lat/add-attr :t0 :query-term nil)
                 (lat/add-attr :t0 :ent-type :todo)
                 (lat/add-attr :t1 :type :ent)
                 (lat/add-attr :t1 :index 1)
                 (lat/add-attr :t1 :query-term nil)
                 (lat/add-attr :t1 :ent-type :todo)
                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term [:tl0 {:todo-id 2}])
                 (lat/add-attr :tl0 :t0 :relation-attrs #{:todo-id})
                 (lat/add-attr :tl0 :t1 :relation-attrs #{:todo-id})))))))

(deftest test-build-ent-db-one-level-has-many-relation
  (testing "can specify ent names in a has-many relationship"
    (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:todo-list [[:tl0 {:todo-id [:my-todo :my-todo-2]}]]}))]
      (is (= (:schema db) td/schema))
      (is (= (:data db)
             (-> (lg/digraph [:todo :my-todo] [:todo :my-todo-2] [:todo-list :tl0] [:tl0 :my-todo] [:tl0 :my-todo-2])
                 (lat/add-attr :todo :type :ent-type)
                 (lat/add-attr :my-todo :type :ent)
                 (lat/add-attr :my-todo :index 0)
                 (lat/add-attr :my-todo :query-term nil)
                 (lat/add-attr :my-todo :ent-type :todo)
                 (lat/add-attr :my-todo-2 :type :ent)
                 (lat/add-attr :my-todo-2 :index 1)
                 (lat/add-attr :my-todo-2 :query-term nil)
                 (lat/add-attr :my-todo-2 :ent-type :todo)
                 (lat/add-attr :todo-list :type :ent-type)
                 (lat/add-attr :tl0 :type :ent)
                 (lat/add-attr :tl0 :index 0)
                 (lat/add-attr :tl0 :ent-type :todo-list)
                 (lat/add-attr :tl0 :query-term [:tl0 {:todo-id [:my-todo :my-todo-2]}])
                 (lat/add-attr :tl0 :my-todo :relation-attrs #{:todo-id})
                 (lat/add-attr :tl0 :my-todo-2 :relation-attrs #{:todo-id})))))))

(deftest test-build-ent-db-one-level-relation-custom-related
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project [[:p1 {:owner-id      :owner0
                                                                           :updated-by-id :owner0}]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :owner0] [:project :p1] [:p1 :owner0])
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :owner0 :type :ent)
               (lat/add-attr :owner0 :index 0)
               (lat/add-attr :owner0 :query-term nil)
               (lat/add-attr :owner0 :ent-type :user)
               (lat/add-attr :project :type :ent-type)
               (lat/add-attr :p1 :type :ent)
               (lat/add-attr :p1 :index 0)
               (lat/add-attr :p1 :ent-type :project)
               (lat/add-attr :p1 :query-term [:p1 {:owner-id      :owner0
                                                   :updated-by-id :owner0}])
               (lat/add-attr :p1 :owner0 :relation-attrs #{:updated-by-id :owner-id}))))))

(deftest test-build-ent-db-two-level-relation
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project-supporter [1]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :u0]
                           [:project :p0]
                           [:supporter :s0]
                           [:project-supporter :ps0]
                           [:ps0 :s0]
                           [:ps0 :p0]
                           [:ps0 :u0]
                           [:p0 :u0]
                           [:s0 :u0])
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
               (lat/add-attr :project-supporter :type :ent-type)
               (lat/add-attr :ps0 :type :ent)
               (lat/add-attr :ps0 :index 0)
               (lat/add-attr :ps0 :ent-type :project-supporter)
               (lat/add-attr :ps0 :query-term nil)
               (lat/add-attr :ps0 :u0 :relation-attrs #{:owner-id})
               (lat/add-attr :ps0 :p0 :relation-attrs #{:project-id})
               (lat/add-attr :ps0 :s0 :relation-attrs #{:supporter-id})
               (lat/add-attr :s0 :u0 :relation-attrs #{:updated-by-id :owner-id})
               (lat/add-attr :p0 :u0 :relation-attrs #{:updated-by-id :owner-id}))))))

(deftest test-build-ent-db-two-level-relation-binding
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project-supporter [[:ps0 {} {:user :bloop}]]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
           (-> (lg/digraph [:user :bloop]
                           [:project :p-bound-ps0-0]
                           [:supporter :s-bound-ps0-0]
                           [:project-supporter :ps0]
                           [:ps0 :s-bound-ps0-0]
                           [:ps0 :p-bound-ps0-0]
                           [:ps0 :bloop]
                           [:p-bound-ps0-0 :bloop]
                           [:s-bound-ps0-0 :bloop])
               (lat/add-attr :user :type :ent-type)
               (lat/add-attr :bloop :type :ent)
               (lat/add-attr :bloop :index 0)
               (lat/add-attr :bloop :ent-type :user)
               (lat/add-attr :bloop :query-term [nil nil {:user :bloop}])
               (lat/add-attr :project :type :ent-type)
               (lat/add-attr :p-bound-ps0-0 :type :ent)
               (lat/add-attr :p-bound-ps0-0 :index 0)
               (lat/add-attr :p-bound-ps0-0 :ent-type :project)
               (lat/add-attr :p-bound-ps0-0 :query-term [nil nil {:user :bloop}])
               (lat/add-attr :supporter :type :ent-type)
               (lat/add-attr :s-bound-ps0-0 :type :ent)
               (lat/add-attr :s-bound-ps0-0 :index 0)
               (lat/add-attr :s-bound-ps0-0 :ent-type :supporter)
               (lat/add-attr :s-bound-ps0-0 :query-term [nil nil {:user :bloop}])
               (lat/add-attr :project-supporter :type :ent-type)
               (lat/add-attr :ps0 :type :ent)
               (lat/add-attr :ps0 :index 0)
               (lat/add-attr :ps0 :ent-type :project-supporter)
               (lat/add-attr :ps0 :query-term [:ps0 {} {:user :bloop}])
               (lat/add-attr :ps0 :bloop :relation-attrs #{:owner-id})
               (lat/add-attr :ps0 :p-bound-ps0-0 :relation-attrs #{:project-id})
               (lat/add-attr :ps0 :s-bound-ps0-0 :relation-attrs #{:supporter-id})
               (lat/add-attr :s-bound-ps0-0 :bloop :relation-attrs #{:updated-by-id :owner-id})
               (lat/add-attr :p-bound-ps0-0 :bloop :relation-attrs #{:updated-by-id :owner-id}))))))

(deftest test-build-ent-db-two-level-relation-uniq-constraint
  (let [db (strip-db (sm/build-ent-db {:schema td/schema} {:project-supporter [2]}))]
    (is (= (:schema db) td/schema))
    (is (= (:data db)
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
               (lat/add-attr :p0 :u0 :relation-attrs #{:updated-by-id :owner-id}))))))

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
  (let [db (-> (sm/build-ent-db {:schema td/schema} {:user [[:_]]})
               (sm/traverse-ents-add-attr :custom-attr-key (constantly nil))
               (sm/traverse-ents-add-attr :custom-attr-key (constantly "yaaaaay a key")))]
    (is (nil? (lat/attr (:data db) :u0 :custom-attr-key)))))

(deftest assert-schema-refs-must-exist
  (is (thrown-with-msg? java.lang.AssertionError
                        #"Your schema relations reference nonexistent types: "
                        (sm/build-ent-db {:schema {:user {:relations {:u1 [:circle :circle-id]}}}} {}))))

(deftest assert-no-dupe-prefixes
  (is (thrown-with-msg? java.lang.AssertionError
                        #"You have used the same prefix for multiple entity types: "
                        (sm/build-ent-db {:schema {:user  {:prefix :u}
                                                   :user2 {:prefix :u}}} {}))))

(deftest enforces-has-many-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for has-many attrs must be a number or vector"
                        (sm/build-ent-db {:schema td/schema} {:ps-list [[:_ {:ps-ids :ps1}]]}))))

(deftest enforces-has-one-schema-constraints
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Query-relations for has-one attrs must be a keyword"
                        (sm/build-ent-db {:schema td/schema} {:project [[:_ {:owner-id [:u1 :u2]}]]}))))

