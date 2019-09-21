(ns reifyhealth.specmonstah-tutorial.12
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]))

(def id-seq (atom 0))
(s/def ::id (s/with-gen pos-int?
              #(gen/fmap (fn [_] (swap! id-seq inc))
                         (gen/return nil))))
(s/def ::topic-id ::id)
(s/def ::first-post-id ::id)

(s/def ::post (s/keys :req-un [::id ::topic-id]))
(s/def ::topic (s/keys :req-un [::id ::first-post-id]))

(s/def ::user (s/keys :req-un [::id]))

(def schema
  {:topic {:prefix    :t
           :spec      ::topic
           :relations {:first-post-id [:post :id]}
           :conform   {:cycle-keys #{:first-post-id}}}
   :post  {:prefix      :p
           :relations   {:topic-id [:topic :id]}
           :constraints {:topic-id #{:required}}
           :spec        ::post}})

(defn ex-01
  []
  (sm/view (sm/add-ents {:schema schema} {:post [[3]]})))

(defn ex-02
  "Shows that `:required` results in correct order"
  []
  (-> (sm/add-ents {:schema schema} {:post [[3]]})
      (sm/visit-ents :print (fn [_ {:keys [ent-name]}] (prn ent-name))))
  nil)

;; One way to deal with a cycle when inserting records is to break up
;; the visiting function into multiple functions.

(def database (atom []))

(defn insert
  "When inserting records, remove any `:cycle-keys` because those keys
  will reference records that haven't been inserted yet."
  [db {:keys [ent-name ent-type visit-query-opts spec-gen schema-opts]}]
  (let [cycle-keys (into (:cycle-keys schema-opts) (:cycle-keys visit-query-opts))
        record     (apply dissoc spec-gen cycle-keys)]
    (swap! database conj [:insert ent-name record])))

(defn update-keys
  "Perform an 'update', setting all cycle keys to the correct value now
  that the referenced record exists"
  [db {:keys [ent-name ent-type visit-query-opts spec-gen schema-opts]}]
  (let [cycle-keys (into (:cycle-keys schema-opts) (:cycle-keys visit-query-opts))]
    (when (seq cycle-keys)
      (swap! database conj [:update ent-name (select-keys spec-gen cycle-keys)]))))

(def conform [insert update-keys])

(defn ex-03
  []
  (reset! database [])
  (reset! id-seq 0)
  (-> (sg/ent-db-spec-gen {:schema schema} {:post [[3]]})
      (sm/visit-ents :conform conform))
  @database)

(defn ex-04
  []
  (reset! database [])
  (reset! id-seq 0)
  (-> (sg/ent-db-spec-gen {:schema schema} {:topic [[1]]})
      (sm/visit-ents :conform conform))
  @database)
