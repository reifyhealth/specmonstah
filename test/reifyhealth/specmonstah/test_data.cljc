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

(s/def ::owner-id ::id)
(s/def ::updated-by-id ::id)

(s/def ::project-name #{"Carrots"})
(s/def ::project (s/keys :req-un [::id ::project-name ::owner-id ::updated-by-id]))

(s/def ::supporter-name #{"Mario"})
(s/def ::supporter (s/keys :req-un [::id ::supporter-name ::owner-id ::updated-by-id]))

(s/def ::supporter-id ::id)
(s/def ::project-id ::id)
(s/def ::project-supporter (s/keys :req-un [::id ::supporter-id ::project-id ::owner-id]))

(s/def ::ps-ids (s/coll-of ::id))
(s/def ::project-supporter-list
  (s/keys :req-un [::ps-ids]))

;; ---------
;; Test refs
;; ---------

(def schema
  {:user              {:spec   ::user
                       :prefix :u}
   :project           {:spec      ::project
                       :relations {:owner-id      [:user :id]
                                   :updated-by-id [:user :id]}
                       :prefix    :p}
   :supporter         {:spec      ::supporter
                       :relations {:owner-id      [:user :id]
                                   :updated-by-id [:user :id]}
                       :prefix    :s}
   :project-supporter {:spec        ::project-supporter
                       :relations   {:project-id   [:project :id]
                                     :supporter-id [:supporter :id]
                                     :owner-id     [:user :id]}
                       :constraints {:supporter-id :uniq}
                       :prefix      :ps}
   :ps-list           {:spec        ::project-supporter-list
                       :relations   {:ps-ids [:project-supporter :id]}
                       :constraints {:ps-ids :has-many}
                       :prefix      :psl}


   :todo      {:spec   ::todo
               :prefix :t}
   :todo-list {:spec        ::todo-list
               :prefix      :tl
               :relations   {:todo-id [:todo :id]}
               :constraints {:todo-id :has-many}}})

(def a->a-schema
  {:user  {:spec      ::user
           :relations {:updated-by-id [:user :id]}
           :prefix    :u}})
