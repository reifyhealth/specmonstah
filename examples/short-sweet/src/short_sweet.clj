(ns short-sweet
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [loom.io :as lio]))

;;-------*****--------
;; Begin example setup
;;-------*****--------

;; ---
;; Define specs for our domain entities

;; The ::id should be a positive int, and to generate it we increment
;; the number stored in `id-seq`. This ensures unique ids and produces
;; values that are easier for humans to understand
(def id-seq (atom 0))
(s/def ::id (s/with-gen pos-int? #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))

(s/def ::username string?)

(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::title string?)
(s/def ::todo-list-id ::id)
(s/def ::assigned-id ::id)
(s/def ::todo (s/keys :req-un [::id ::title ::todo-list-id ::assigned-id]))

(s/def ::owner-id ::id)
(s/def ::todo-list (s/keys :req-un [::id ::owner-id ::title]))


;; ---
;; The schema defines specmonstah `ent-types`, which roughly
;; correspond to db tables. It also defines the `:spec` for generting
;; ents of that type, and defines ent `relations`
(def schema
  {:user      {:spec   ::user
               :prefix :u}
   :todo-list {:spec      ::todo-list
               :relations {:owner-id [:user :id]}
               :prefix    :tl}
   :todo      {:spec      ::todo
               
               :relations {:assigned-id  [:user :id]
                           :todo-list-id [:todo-list :id]}
               :spec-gen  {:title "default todo title"}
               :prefix    :t}})

;; A vector of inserted records we can use to show that entities are
;; inserted in the correct order
(def example-db (atom []))

(defn insert*
  "Simulates inserting records in a db by conjing values onto an atom"
  [{:keys [data] :as db} {:keys [ent-type spec-gen]}]
  (swap! example-db conj [ent-type spec-gen]))

(defn insert [query]
  (reset! id-seq 0)
  (reset! example-db [])
  (-> (sg/ent-db-spec-gen {:schema schema} query)
      (sm/visit-ents-once :inserted-data insert*))
  ;; normally you'd return the expression above, but return nil for
  ;; the example to not produce overwhelming output
  nil)


;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; Return a map of user entities and their spec-generated data
(-> (sg/ent-db-spec-gen {:schema schema} {:user [[3]]})
    (sm/attr-map :spec-gen))

;; You can specify a username and id
(-> (sg/ent-db-spec-gen {:schema schema} {:user [[1 {:spec-gen {:username "Meeghan"
                                                                :id       100}}]]})
    (sm/attr-map :spec-gen))

;; Generating a todo-list generates the user the todo-list belongs,
;; with foreign keys correct
(-> (sg/ent-db-spec-gen {:schema schema} {:todo-list [[1]]})
    (sm/attr-map :spec-gen))

;; Generating a todo also generates a todo list and user
(-> (sg/ent-db-spec-gen {:schema schema} {:todo [[1]]})
    (sm/attr-map :spec-gen))


;; The `insert` function shows that records are inserted into the
;; simulate "database" (`example-db`) in correct dependency order:
(insert {:todo [[1]]})
@example-db
