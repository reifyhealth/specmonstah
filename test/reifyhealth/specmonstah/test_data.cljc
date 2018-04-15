(ns reifyhealth.specmonstah.test-data
  (:require #?(:clj [clojure.test :refer [deftest is are use-fixtures testing]]
               :cljs [cljs.test :include-macros true])
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen :include-macros true]))

(def id-seq (atom 0))

(defn test-fixture [f]
  (reset! id-seq 0)
  (f))

(s/def ::id
  (s/with-gen
    pos-int?
    #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))


(s/def ::user-name #{"Luigi"})
(s/def ::user (s/keys :req-un [::id ::user-name]))

(s/def ::created-by-id ::id)
(s/def ::updated-by-id ::id)

(s/def ::todo-title #{"write unit tests"})
(s/def ::todo (s/keys :req-un [::id ::todo-title ::created-by-id ::updated-by-id]))

(s/def ::todo-id ::id)
(s/def ::attachment (s/keys :req-un [::id ::todo-id ::created-by-id ::updated-by-id]))

(s/def ::todo-list (s/keys :req-un [::id ::created-by-id ::updated-by-id]))

(s/def ::todo-list-id ::id)
(s/def ::watcher-id ::id)
(s/def ::todo-list-watcher (s/keys :req-un [::id ::todo-list-id ::watcher-id]))

;; It's probably unrealistic to have groups of watchers, but we need
;; to be able to test the combination of a parent that :coll
;; children, where the children have a :uniq constraint
(s/def ::todo-list-watcher-ids ::id)
(s/def ::watcher-group (s/keys :req-un [::id ::todo-list-watcher-ids]))

;; In THE REAL WORLD todo-list would probably have a project-id,
;; rather than project having some coll of :todo-list-ids
(s/def ::todo-list-ids (s/coll-of ::todo-list-id))
(s/def ::project (s/keys :req-un [::id ::todo-list-ids ::created-by-id ::updated-by-id]))

(def schema
  {:user            {:spec   ::user
                     :prefix :u}
   :attachment      {:spec      ::attachment
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-id       [:todo :id]}
                     :prefix    :a}
   :todo            {:spec      ::todo
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]
                                 :todo-list-id  [:todo-list :id]}
                     :prefix    :t}
   :todo-list       {:spec      ::todo-list
                     :relations {:created-by-id [:user :id]
                                 :updated-by-id [:user :id]}
                     :prefix    :tl}
   :todo-list-watch {:spec        ::todo-list-watch
                     :relations   {:todo-list-id [:todo-list :id]
                                   :watcher-id   [:user :id]}
                     :constraints {:todo-list-id :uniq}
                     :prefix      :tlw}
   :project         {:spec        ::project
                     :relations   {:created-by-id [:user :id]
                                   :updated-by-id [:user :id]
                                   :todo-list-ids [:todo-list :id]}
                     :constraints {:todo-list-ids :coll}
                     :prefix      :p}})
