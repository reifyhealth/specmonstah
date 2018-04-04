(ns reifyhealth.specmonstah.spec-gen-test
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

(def gen-data-db (atom []))

(defn reset-gen-data-db [f]
  (reset! gen-data-db [])
  (f))

(use-fixtures :each td/test-fixture reset-gen-data-db)

(deftest test-spec-gen
  (is (= (sg/ent-db-spec-gen-data {:schema td/schema} {:project [1]})
         {:u0 {:id 1
               :user-name "Luigi"}
          :p0 {:id 2
               :project-name "Carrots"
               :owner-id 1
               :updated-by-id 1}})))

(deftest test-spec-gen-nested
  (is (= (sg/ent-db-spec-gen-data {:schema td/schema} {:ps-list [[:psl0 {:ps-ids 3}]]})
         {:u0   {:id 1 :user-name "Luigi"}
          :s0   {:id 2 :supporter-name "Mario" :owner-id 1 :updated-by-id 1}
          :s1   {:id 5 :supporter-name "Mario" :owner-id 1 :updated-by-id 1}
          :s2   {:id 8 :supporter-name "Mario" :owner-id 1 :updated-by-id 1}
          :p0   {:id 11 :project-name "Carrots" :owner-id 1 :updated-by-id 1}
          :ps0  {:id 14 :supporter-id 2 :project-id 11 :owner-id 1}
          :ps1  {:id 18 :supporter-id 5 :project-id 11 :owner-id 1}
          :ps2  {:id 22 :supporter-id 8 :project-id 11 :owner-id 1}
          :psl0 {:ps-ids [14 18 22]}})))

(deftest test-spec-gen-manual-attr
  (is (= (sg/ent-db-spec-gen-data {:schema td/schema} {:project [[:p0 nil nil {:project-name "Peas"}]]})
         {:u0 {:id 1
               :user-name "Luigi"}
          :p0 {:id 2
               :project-name "Peas"
               :owner-id 1
               :updated-by-id 1}})))

(deftest test-idempotency
  (testing "Gen traversal won't replace already generated data with newly generated data"
    (let [gen-fn     #(sg/ent-db-spec-gen % {:project [[:p0 nil nil {:project-name "Peas"}]]})
          first-pass (gen-fn {:schema td/schema})]
      (is (= first-pass (gen-fn first-pass))))))

(def insert (sg/traverse-spec-gen-data-fn
              (fn [db ent-data ent-type ent-name]
                (swap! gen-data-db conj [ent-type ent-name ent-data]))))

(deftest test-insert-gen-data
  (-> (sg/ent-db-spec-gen {:schema td/schema} {:project [1]})
      (sm/traverse-ents-add-attr :inserted-data insert))
  (is (= @gen-data-db
         [[:user :u0 {:id 1 :user-name "Luigi"}]
          [:project :p0 {:id 2 :project-name "Carrots" :owner-id 1 :updated-by-id 1}]])))

(deftest inserts-novel-data
  (let [db1 (-> (sg/ent-db-spec-gen {:schema td/schema} {:project [1]})
                (sm/traverse-ents-add-attr :inserted-data insert))]
    (-> (sg/ent-db-spec-gen db1 {:user [[:u1]]})
        (sm/traverse-ents-add-attr :inserted-data insert))
    (is (= @gen-data-db
           [[:user :u0 {:id 1 :user-name "Luigi"}]
            [:project :p0 {:id 2 :project-name "Carrots" :owner-id 1 :updated-by-id 1}]
            [:user :u1 {:id 5 :user-name "Luigi"}]]))))
