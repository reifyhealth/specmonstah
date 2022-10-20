(ns reifyhealth.specmonstah.generate-test
  (:require #?(:clj [clojure.test :refer [deftest are is use-fixtures testing]]
               :cljs [cljs.test :include-macros true :refer [deftest are is use-fixtures testing]])
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [malli.core :as malli]
            [medley.core :as m]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.generate :as generate]
            [reifyhealth.specmonstah.test-data :as td]
            [reifyhealth.specmonstah.test-data :as td]
            [reifyhealth.specmonstah.generate.malli]))

(def gen-data-db (atom []))
(def gen-data-cycle-db (atom []))

(defn reset-dbs [f]
  (reset! gen-data-db [])
  (reset! gen-data-cycle-db [])
  (f))

(use-fixtures :each td/test-fixture reset-dbs)

(defn ids-present?
  [generated]
  (every? pos-int? (map :id (vals generated))))

(defn only-has-ents?
  [generated ent-names]
  (= (set (keys generated))
     (set ent-names)))

(defn ids-match?
  "Reference attr vals equal their referent"
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

;; Rename :spec-gen to :set in the entity schemas
(def generate-schema
  (reduce-kv (fn [acc ent-type schema]
               (if-let [overwrite (:spec-gen schema)]
                 (assoc-in acc [ent-type :set] overwrite)
                 acc))
             td/schema
             td/schema))

(def base-schema
  "schema with no generation mechanism"
  {:user {:relations {:updated-by-id [:user :id]}
          :prefix    :u}})

(defn- quick-gen
  [schema query & opts]
  (-> (apply generate/generate {:schema schema} query opts)
      (generate/attrs)))

(deftest malli-test
  (testing "we can use malli to generate entities"
    (let [malli [:map
                 [:id            :int]
                 [:updated-by-id :int]
                 [:user-name     :string]]]
      (is (->> (quick-gen base-schema {:user [[1 {:malli malli}]]})
               :u0
               (malli/validate malli))))))


(s/def ::query-generated #{true})
(s/def ::user (s/merge ::td/user (s/keys :req-un [::query-generated])))

(deftest spec-test
  (testing "we can use spec to generate entities just like spec-gen"
    (is (->> (quick-gen base-schema {:user [[1 {:spec ::user}]]})
             :u0
             (s/valid? ::user)))))

(deftest function-test
  (testing "we can use a function to generate the entity"
    (let [expected {:id            1
                    :updated-by-id 1
                    :user-name     "Billy"}]
      (is (= expected
             (:u0 (quick-gen base-schema
                             {:user [[1 {:fn (constantly expected)}]]})))))))

(deftest test-check-generators-test
  (testing "we can use test check generators to generate entities"
    (is (->> (quick-gen base-schema
                        {:user [[1 {:generator
                                    (gen/hash-map
                                     :id              gen/int
                                     :updated-by-id   gen/int
                                     :user-name       gen/string-alpha-numeric
                                     :query-generated (gen/return true))}]]})
             :u0
             (s/explain-data ::user)))))


(deftest query-override-test
  (let [schema (assoc-in base-schema [:user :fn]
                         (constantly {:schema-generated true}))]
    (testing "entity is generated from the schema by default"
      (is (true? (get-in (quick-gen schema {:user [[1]]})
                         [:u0 :schema-generated]))))
    (testing "the query can be used to override the schema"
      (are [query] (td/submap? {:u0 {:query-generated true}}
                               (quick-gen schema {:user [[1 query]]}))
        {:fn (fn [] {:query-generated true})}
        {:generator (gen/hash-map :query-generated (gen/return true))}
        {:malli [:map [:query-generated [:enum true]]]}
        {:spec ::user}))))

(deftest generation-strategies-order-test
  (is (= {:malli true, :schema true, :updated-by-id nil}
         (:u0 (quick-gen (assoc-in base-schema [:user :malli]
                                   [:map
                                    [:malli [:enum true]]
                                    [:schema [:enum true]]])
                         {:user [[1 {:fn (constantly {:fn-generated true})}]]}
                         :strategies [:malli])))
      "fn strategy from the query is ignored as only malli is selected")
  (is (= {:malli true, :schema true, :updated-by-id nil}
         (:u0 (quick-gen (-> base-schema
                             (assoc-in [:user :malli] [:map
                                                       [:malli [:enum true]]
                                                       [:schema [:enum true]]])
                             (assoc-in [:user :fn]
                                       (constantly {:fn true, :schema true})))
                         {:user [[1]]}
                         :strategies [:malli :fn])))
      "malli is selected before fn as it's higher on the strategy list")
  (is (= {:fn true, :query true, :updated-by-id nil}
         (:u0 (quick-gen (-> base-schema
                             (assoc-in [:user :malli] [:map
                                                       [:malli [:enum true]]
                                                       [:schema [:enum true]]])
                             (assoc-in [:user :fn]
                                       (constantly {:fn true, :schema true})))
                         {:user [[1 {:fn (constantly {:fn    true
                                                      :query true})}]]}
                         :strategies [:malli :fn])))
      "the query is checked before the schema regardless of strategy order"))

(deftest test-generate
  (let [gen (quick-gen generate-schema {:todo-list [[1]]})]
    (is (td/submap? {:u0 {:user-name "Luigi"}} gen))
    (is (ids-present? gen))
    (is (ids-match? gen
                    {:tl0 {:created-by-id [:u0 :id]
                           :updated-by-id [:u0 :id]}}))
    (is (only-has-ents? gen #{:tl0 :u0}))))

(deftest test-generate-nested
  (let [gen (quick-gen generate-schema {:project [[:_ {:refs {:todo-list-ids 3}}]]})]
    (is (td/submap? {:u0 {:user-name "Luigi"}} gen))
    (is (ids-present? gen))
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
                                           [:tl2 :id]]}}))
    (is (only-has-ents? gen #{:tl0 :tl1 :tl2 :u0 :p0}))))

(deftest test-generate-manual-attr
  (testing "Manual attribute setting for non-reference field"
    (let [gen (quick-gen generate-schema {:todo [[:_ {:set {:todo-title "pet the dog"}}]]})]
      (is (td/submap? {:u0 {:user-name "Luigi"}
                       :t0 {:todo-title "pet the dog"}}
                      gen))
      (is (ids-present? gen))
      (is (ids-match? gen
                      {:tl0 {:created-by-id [:u0 :id]
                             :updated-by-id [:u0 :id]}
                       :t0  {:created-by-id [:u0 :id]
                             :updated-by-id [:u0 :id]
                             :todo-list-id  [:tl0 :id]}}))
      (is (only-has-ents? gen #{:tl0 :t0 :u0}))))

  (testing "Manual attribute setting for reference field"
    (let [gen (quick-gen generate-schema {:todo [[:_ {:set {:created-by-id 1}}]]})]
      (is (td/submap? {:u0 {:user-name "Luigi"}
                       :t0 {:created-by-id 1}}
                      gen))
      (is (ids-present? gen))
      (is (ids-match? gen
                      {:tl0 {:created-by-id [:u0 :id]
                             :updated-by-id [:u0 :id]}
                       :t0  {:updated-by-id [:u0 :id]
                             :todo-list-id  [:tl0 :id]}}))
      (is (only-has-ents? gen #{:tl0 :t0 :u0})))))

(deftest test-generate-omit
  (testing "Ref not created and attr is not present when omitted"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:refs {:created-by-id ::sm/omit
                                                                  :updated-by-id ::sm/omit}}]]})]
      (is (ids-present? gen))
      (is (only-has-ents? gen #{:tl0}))
      (is (= [:id] (keys (:tl0 gen))))))

  (testing "Ref is created when at least 1 field references it, but omitted attrs are still not present"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:refs {:updated-by-id ::sm/omit}}]]})]
      (is (td/submap? {:u0 {:user-name "Luigi"}} gen))
      (is (ids-present? gen))
      (is (ids-match? gen
                      {:tl0 {:created-by-id [:u0 :id]}}))
      (is (only-has-ents? gen #{:tl0 :u0}))
      (is (= [:id :created-by-id] (keys (:tl0 gen))))))

  (testing "Overwriting value of omitted ref with custom value"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:refs {:updated-by-id ::sm/omit}
                                                           :set  {:updated-by-id 42}}]]})]
      (is (ids-present? gen))
      (is (= 42 (-> gen :tl0 :updated-by-id)))))

  (testing "Overwriting value of omitted ref with nil"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:refs {:updated-by-id ::sm/omit}
                                                           :set  {:updated-by-id nil}}]]})]
      (is (ids-present? gen))
      (is (= nil (-> gen :tl0 :updated-by-id))))))

(deftest overwriting
  (testing "Overwriting generated value with query map"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:set {:updated-by-id 42}}]]})]
      (is (ids-present? gen))
      (is (= 42 (-> gen :tl0 :updated-by-id)))))

  (testing "Overwriting generated value with query fn"
    (let [gen (quick-gen generate-schema {:todo-list [[:_ {:set #(assoc % :updated-by-id :foo)}]]})]
      (is (ids-present? gen))
      (is (= :foo (-> gen :tl0 :updated-by-id)))))

  (testing "Overwriting generated value with schema map"
    (let [gen (quick-gen (assoc-in generate-schema [:todo :set :todo-title] "schema title")
                         {:todo [[:_ {:set #(assoc % :updated-by-id :foo)}]]})]
      (is (ids-present? gen))
      (is (= "schema title" (-> gen :t0 :todo-title)))))

  (testing "Overwriting generated value with schema fn"
    (let [gen (quick-gen (assoc-in generate-schema [:todo :set] #(assoc % :todo-title "boop whooop"))
                         {:todo [[:_ {:set #(assoc % :updated-by-id :foo)}]]})]
      (is (ids-present? gen))
      (is (= "boop whooop" (-> gen :t0 :todo-title))))))

(deftest test-idempotency
  (testing "Gen traversal won't replace already generated data with newly generated data"
    (let [gen-fn     #(generate/generate % {:todo [[:t0 {:set {:todo-title "pet the dog"}}]]})
          first-pass (gen-fn {:schema generate-schema})]
      (is (= (:data first-pass)
             (:data (gen-fn first-pass)))))))


(deftest test-coll-relval-order
  (testing "When a relation has a `:coll` constraint, order its vals correctly")
  (let [gen (quick-gen generate-schema {:project [[:_ {:refs {:todo-list-ids 3}}]]})]
    (is (td/submap? {:u0 {:user-name "Luigi"}} gen))
    (is (ids-present? gen))
    (is (= (:todo-list-ids (:p0 gen))
           [(:id (:tl0 gen))
            (:id (:tl1 gen))
            (:id (:tl2 gen))]))
    (is (only-has-ents? gen #{:tl0 :tl1 :tl2 :u0 :p0}))))

(deftest test-sets-custom-relation-val
  (let [gen (quick-gen generate-schema
                       {:user      [[:custom-user {:set {:id 100}}]]
                        :todo-list [[:custom-tl {:refs {:created-by-id :custom-user
                                                        :updated-by-id :custom-user}}]]})]
    (is (td/submap? {:custom-user {:user-name "Luigi"
                                   :id        100}}
                    gen))
    (is (ids-present? gen))
    (is (ids-match? gen
                    {:custom-tl {:created-by-id [:custom-user :id]
                                 :updated-by-id [:custom-user :id]}}))
    (is (only-has-ents? gen #{:custom-tl :custom-user}))))

;; testing inserting
(defn insert
  [{:keys [data] :as db} {:keys [ent-name visit-key attrs]}]
  (swap! gen-data-db conj [(:ent-type attrs)
                           ent-name
                           (::generate/generated attrs)]))

(deftest test-insert-gen-data
  (-> (generate/generate {:schema generate-schema} {:todo [[1]]})
      (sm/visit-ents-once :inserted-data insert))

  ;; gen data is something like:
  ;; [[:user :u0 {:id 1 :user-name "Luigi"}]
  ;;  [:todo-list :tl0 {:id 2 :created-by-id 1 :updated-by-id 1}]
  ;;  [:todo :t0 {:id            5
  ;;              :todo-title    "write unit tests"
  ;;              :created-by-id 1
  ;;              :updated-by-id 1
  ;;              :todo-list-id  2}]]

  (let [gen-data @gen-data-db]
    (is (= (set (map #(take 2 %) gen-data))
           #{[:user :u0]
             [:todo-list :tl0]
             [:todo :t0]}))

    (let [ent-map (into {} (map #(vec (drop 1 %)) gen-data))]
      (is (td/submap? {:u0 {:user-name "Luigi"}
                       :t0 {:todo-title "write unit tests"}}
                      ent-map))
      (is (ids-present? ent-map))
      (is (ids-match? ent-map
                      {:tl0 {:created-by-id [:u0 :id]
                             :updated-by-id [:u0 :id]}
                       :t0  {:created-by-id [:u0 :id]
                             :updated-by-id [:u0 :id]
                             :todo-list-id  [:tl0 :id]}})))))

(deftest inserts-novel-data
  (testing "Given a db with a todo already added, next call adds a new
  todo that references the same todo list and user"
    (let [db1 (-> (generate/generate {:schema generate-schema} {:todo [[1]]})
                  (sm/visit-ents-once :inserted-data insert))]
      (-> (generate/generate db1 {:todo [[1]]})
          (sm/visit-ents-once :inserted-data insert))

      (let [gen-data @gen-data-db]
        (is (= (set (map #(take 2 %) gen-data))
               #{[:user :u0]
                 [:todo-list :tl0]
                 [:todo :t0]
                 [:todo :t1]}))

        (let [ent-map (into {} (map #(vec (drop 1 %)) gen-data))]
          (is (td/submap? {:u0 {:user-name "Luigi"}
                           :t0 {:todo-title "write unit tests"}
                           :t1 {:todo-title "write unit tests"}}
                          ent-map))
          (is (ids-present? ent-map))
          (is (ids-match? ent-map
                          {:tl0 {:created-by-id [:u0 :id]
                                 :updated-by-id [:u0 :id]}
                           :t0  {:created-by-id [:u0 :id]
                                 :updated-by-id [:u0 :id]
                                 :todo-list-id  [:tl0 :id]}
                           :t1  {:created-by-id [:u0 :id]
                                 :updated-by-id [:u0 :id]
                                 :todo-list-id  [:tl0 :id]}})))))))

(defn insert-cycle
  [db {:keys [ent-name visit-key]}]
  (swap! gen-data-cycle-db conj ent-name)
  (sm/ent-attr db ent-name ::generate/generated))

(deftest handle-cycles-with-constraints-and-reordering
  (testing "todo-list is inserted before todo because todo requires todo-list"
    (-> (generate/generate {:schema td/cycle-schema} {:todo [[1]]})
        (sm/visit-ents :insert-cycle insert-cycle))
    (is (= @gen-data-cycle-db
           [:tl0 :t0]))))

(deftest handles-cycle-ids
  (testing "generate correctly sets foreign keys for cycles"
    (let [gen (quick-gen td/cycle-schema {:todo [[1]]})]
      (is (ids-present? gen))
      (is (ids-match? gen
                      {:t0  {:todo-list-id [:tl0 :id]}
                       :tl0 {:first-todo-id [:t0 :id]}})))))


;; NOTE: do not use this strategy or all you entities will be the same
(defmethod generate/generate-entity ::const [_ const] const)

(deftest custom-generation-strategy-test
  (is (= {:custom true, :updated-by-id nil}
         (:u0 (quick-gen (assoc-in base-schema [:user ::const] {:custom true})
                         {:user [[1]]})))
      "Select custom strategy from the schema")
  (is (= {:custom true, :updated-by-id nil}
         (:u0 (quick-gen base-schema
                         {:user [[1 {::const {:custom true}}]]})))
      "Select custom strategy from the query"))
