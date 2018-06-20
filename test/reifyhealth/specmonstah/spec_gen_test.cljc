(ns reifyhealth.specmonstah.spec-gen-test
  (:require #?(:clj [clojure.test :refer [deftest is are use-fixtures testing]]
               :cljs [cljs.test :include-macros true :refer [deftest is are use-fixtures testing]])
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen :include-macros true]
            [reifyhealth.specmonstah.test-data :as td]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [medley.core :as medley]
            [loom.graph :as lg]
            [loom.alg :as la]
            [loom.attr :as lat]))

(def gen-data-db (atom []))
(def gen-data-cycle-db (atom []))

(defn reset-gen-data-db [f]
  (reset! gen-data-db [])
  (reset! gen-data-cycle-db [])
  (f))

(use-fixtures :each td/test-fixture reset-gen-data-db)

(defn selected-keys=
  [m1 m2]
  (every? (fn [k]
            (let [m2-inner (k m2)]
              (= (select-keys (k m1) (keys m2-inner))
                 m2-inner)))
          (keys m2)))

(defn ids-present?
  [generated ids-map]
  (every? (fn [[ent ids]]
            (every? (fn [id] (pos-int? (get-in generated [ent id])))
                    ids))
          ids-map))

(defn ids-match?
  [generated matches]
  (every? (fn [[ent id-path-map]]
            (every? (fn [[attr id-path-or-paths]]
                      (if (vector? (first id-path-or-paths))
                        (= (set (map (fn [id-path] (get-in generated id-path)) id-path-or-paths))
                           (set (get-in generated [ent attr])))
                        (= (get-in generated id-path-or-paths)
                           (get-in generated [ent attr]))))
                    id-path-map))
          matches))

(deftest test-spec-gen
  (let [gen (sg/ent-db-spec-gen-attr {:schema td/schema} {:todo-list [[1]]})]
    (is (selected-keys= gen
                        {:u0  {:user-name "Luigi"}}))
    (is (ids-present? gen
                      {:u0  [:id]
                       :tl0 [:id]}))

    (is (ids-match? gen
                    {:tl0 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}}))))

(deftest test-spec-gen-nested
  (let [gen (sg/ent-db-spec-gen-attr {:schema td/schema} {:project [[:_ {:refs {:todo-list-ids 3}}]]})]
    (is (selected-keys= gen
                        {:u0  {:user-name "Luigi"}}))

    (is (ids-present? gen
                      {:u0  [:id]
                       :tl0 [:id]
                       :tl1 [:id]
                       :tl2 [:id]
                       :p0  [:id]}))
    (is (ids-match? gen
                    {:tl0 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}
                     :tl1 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}
                     :tl2 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}
                     :p0  {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]
                           :todo-list-ids [[:tl0 :id]
                                           [:tl1 :id]
                                           [:tl2 :id]]}}))))

(deftest test-spec-gen-manual-attr
  (let [gen (sg/ent-db-spec-gen-attr {:schema td/schema} {:todo [[:_ {:spec-gen {:todo-title "pet the dog"}}]]})]
    (is (selected-keys= gen
                        {:u0 {:user-name "Luigi"}
                         :t0 {:todo-title "pet the dog"}}))
    (is (ids-present? gen
                      {:u0  [:id]
                       :tl0 [:id]
                       :t0  [:id]}))
    (is (ids-match? gen
                    {:tl0 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}
                     :t0 {:created-by-id [:u0 :id]
                          :updated-by-id [:u0 :id]
                          :todo-list-id [:tl0 :id]}}))))

(deftest test-idempotency
  (testing "Gen traversal won't replace already generated data with newly generated data"
    (let [gen-fn     #(sg/ent-db-spec-gen % {:todo [[:t0 {:spec-gen {:todo-title "pet the dog"}}]]})
          first-pass (gen-fn {:schema td/schema})]
      (is (= (:data first-pass)
             (:data (gen-fn first-pass)))))))

(defn insert
  [{:keys [data] :as db} ent-name ent-attr-key]
  (swap! gen-data-db conj [(lat/attr data ent-name :ent-type)
                           ent-name
                           (lat/attr data ent-name sg/spec-gen-ent-attr-key)]))

#_(deftest test-insert-gen-data
    (-> (sg/ent-db-spec-gen {:schema td/schema} {:todo [[1]]})
        (sm/map-ents-attr-once :inserted-data insert))
    (is (= @gen-data-db
           [[:user :u0 {:id 1 :user-name "Luigi"}]
            [:todo-list :tl0 {:id 2 :created-by-id 1 :updated-by-id 1}]
            [:todo :t0 {:id            5
                        :todo-title    "write unit tests"
                        :created-by-id 1
                        :updated-by-id 1
                        :todo-list-id  2}]])))

#_(deftest inserts-novel-data
    (testing "Given a db with a todo already added, next call adds a new
  todo that references the same todo list and user"
      (let [db1 (-> (sg/ent-db-spec-gen {:schema td/schema} {:todo [[1]]})
                    (sm/map-ents-attr-once :inserted-data insert))]
        (-> (sg/ent-db-spec-gen db1 {:todo [[1]]})
            (sm/map-ents-attr-once :inserted-data insert))
        (is (= @gen-data-db
               [[:user :u0 {:id 1 :user-name "Luigi"}]
                [:todo-list :tl0 {:id 2 :created-by-id 1 :updated-by-id 1}]
                [:todo :t0 {:id            5
                            :todo-title    "write unit tests"
                            :created-by-id 1
                            :updated-by-id 1
                            :todo-list-id  2}]
                [:todo :t1 {:id            8
                            :todo-title    "write unit tests"
                            :created-by-id 1
                            :updated-by-id 1
                            :todo-list-id  2}]])))))

(defn insert-cycle
  [{:keys [data] :as db} ent-name ent-attr-key]
  (let [{:keys [constraints relations]} (sm/ent-schema db ent-name)
        required-attrs                  (or (keys (medley/filter-vals (fn [val] (contains? val :required))
                                                                      constraints))
                                            [])]
    (if (every? (fn [required-attr]
                  (let [ent-ref (sm/related-ents-by-attr db ent-name required-attr)]
                    (get-in (lat/attr data ent-ref ent-attr-key)
                            (rest (get relations required-attr)))))
                required-attrs)
      (do (swap! gen-data-cycle-db conj ent-name)
          (lat/attr data ent-name sg/spec-gen-ent-attr-key))
      ::sm/map-ent-move-to-end)))

(deftest handle-cycles-with-constraints-and-reordering
  (-> (sg/ent-db-spec-gen {:schema td/cycle-schema} {:todo [[1]]})
      (sm/map-ents-attr :insert-cycle insert-cycle))
  (is (= @gen-data-cycle-db
         [:tl0 :t0])))

(deftest throws-exception-on-2nd-map-ent-attr-try
  ())
