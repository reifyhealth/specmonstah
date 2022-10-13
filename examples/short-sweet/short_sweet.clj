(ns short-sweet
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.franken-gen :as fg]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]))

;;-------*****--------
;; Begin example setup
;;-------*****--------

;; ---
;; Define specs for our domain entities

;; The ::id should be a positive int, and to generate it we increment
;; the number stored in `id-seq`. This ensures unique ids and produces
;; values that are easier for humans to understand
(def id-seq (atom 0))
(defn gen-id [_] (swap! id-seq inc))

(s/def ::id (s/with-gen pos-int? #(gen/fmap gen-id (gen/return nil))))

(s/def ::username (s/and string? not-empty #(< (count %) 10)))
(s/def ::user     (s/keys :req-un [::id ::username]))

;; ---
;; If specs aren't your thing then you can use malli, or test.check generators,
;; or even plain functions

;; Malli schema
(def post
  [:map {:registry {::id [:schema {:gen/fmap gen-id} pos-int?]}}
   [:id            ::id]
   [:created-by-id ::id]
   [:content       [:string {:min 1, :max 10}]]])

;; Test Check Generator
(def like
  (let [id-generator (gen/fmap gen-id (gen/return nil))]
    (gen/hash-map :id            id-generator
                  :post-id       id-generator
                  :created-by-id id-generator)))

;; ---
;; The schema defines specmonstah `ent-types`, which roughly
;; correspond to db tables. It also defines the default generation mechanism
;; for ents of that type (e.g `:spec`, `:malli`, `:generator`, `:fn`), and
;; defines ent `relations` that specify how ents reference each other
(def schema
  {:user {:prefix :u
          :spec   ::user}
   :post {:prefix    :p
          :malli     post
          :relations {:created-by-id [:user :id]}}
   :like {:prefix      :l
          :generator   like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})

;; Our "db" is a vector of inserted records we can use to show that
;; entities are inserted in the correct order
(def mock-db (atom []))

(defn insert*
  "Simulates inserting records in a db by conjing values onto an
  atom. ent-type is `:user`, `:post`, or `:like`, corresponding to the
  keys in the schema. entity is the generated data."
  [_db ent-type entity]
  (swap! mock-db conj [ent-type entity]))

(defn insert [query]
  (reset! id-seq 0)
  (reset! mock-db [])
  (fg/generate {:schema schema} query :insert! insert*)
  ;; normally you'd return the expression above, but return nil for
  ;; the example to not produce overwhelming output
  nil)

;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; Return a map of user entities and their generated data
(-> (fg/generate {:schema schema} {:user [[3]]})
    fg/attrs)

;; You can specify a username and id
(-> (fg/generate {:schema schema} {:user [[1 {:set {:username "Meeghan"
                                                    :id       100}}]]})
    fg/attrs)

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(-> (fg/generate {:schema schema} {:post [[1]]})
    fg/attrs)

;; Generating a like also generates a post and user
(-> (fg/generate {:schema schema} {:like [[1]]})
    fg/attrs)


;; The `insert` function shows that records are inserted into the
;; simulate "database" (`mock-db`) in correct dependency order:
(insert {:like [[1]]})
@mock-db
