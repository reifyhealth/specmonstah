(ns reifyhealth.infomercial
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [loom.attr :as lat]))

(def id-seq (atom 0))
(s/def ::id (s/with-gen pos-int? #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))

(s/def ::not-empty-string (s/and string? not-empty #(< (count %) 20)))
(s/def ::created-by-id ::id)
(s/def ::updated-by-id ::id)

(s/def ::username ::not-empty-string)
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::name ::not-empty-string)
(s/def ::topic-category (s/keys :req-un [::id ::created-by-id ::updated-by-id]))

(s/def ::topic-category-id ::id)
(s/def ::title ::not-empty-string)
(s/def ::topic (s/keys :req-un [::id ::topic-category-id ::title ::created-by-id ::updated-by-id]))

(s/def ::content ::not-empty-string)
(s/def ::topic-id ::id)
(s/def ::post (s/keys :req-un [::id ::topic-id ::created-by-id ::updated-by-id]))

(s/def ::post-id ::id)
(s/def ::like (s/keys :req-un [::id ::post-id ::created-by-id]))

(s/def ::liked-id ::id)
(s/def ::polymorphic-like (s/keys :req-un [::id ::liked-id ::created-by-id]))

(def schema
  {:user             {:prefix :u
                      :spec   ::user}
   :topic-category   {:prefix    :tc
                      :spec      ::topic-category
                      :relations {:created-by-id [:user :id]
                                  :updated-by-id [:user :id]}}
   :topic            {:prefix    :t
                      :spec      ::topic
                      :relations {:topic-category-id [:topic-category :id]
                                  :created-by-id     [:user :id]
                                  :updated-by-id     [:user :id]}}
   :post             {:prefix    :p
                      :spec      ::post
                      :relations {:topic-id      [:topic :id]
                                  :created-by-id [:user :id]
                                  :updated-by-id [:user :id]}}
   :like             {:prefix      :l
                      :spec        ::like
                      :relations   {:post-id       [:post :id]
                                    :created-by-id [:user :id]}
                      :constraints {:created-by-id #{:uniq}}}
   :polymorphic-like {:prefix      :pl
                      :spec        ::polymorphic-like
                      :relations   {:liked-id      #{[:post :id]
                                                     [:topic :id]}
                                    :created-by-id [:user :id]}
                      :constraints {:created-by-id #{:uniq}}}})

(def mock-db (atom []))


(defn insert*
  [{:keys [data] :as db} {:keys [ent-type spec-gen]}]
  (swap! mock-db conj [ent-type spec-gen]))

(defn insert [query]
  (reset! id-seq 0)
  (reset! mock-db [])
  (-> (sg/ent-db-spec-gen {:schema schema} query)
      (sm/visit-ents-once :inserted-data insert*)))

(insert {:post [[1]]})
@mock-db

[[:user {:id 1 :username "K7X5r6UVs9Mm2Eks"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:topic {:id 5
          :topic-category-id 2
          :title "ejJ2B88UZo2NK2sMuU4"
          :created-by-id 1
          :updated-by-id 1}]
 [:post {:id 9 :topic-id 5 :created-by-id 1 :updated-by-id 1}]]


(insert {:topic [[:t0 {:refs {:created-by-id :custom-user}}]]
         :post [[1]]})
@mock-db

[[:user {:id 1 :username "gMKGTwBnOvB0xt"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:user {:id 5 :username "2jK0TXCU2UcBM89"}]
 [:topic {:id 6
          :topic-category-id 2
          :title "cmo2Vg8DQByz302c"
          :created-by-id 5
          :updated-by-id 1}]
 [:post {:id 10 :topic-id 6 :created-by-id 1 :updated-by-id 1}]]


(insert {:post [[3]]})
@mock-db
[[:user {:id 1 :username "yB96fd"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:topic {:id 5
          :topic-category-id 2
          :title "KEh29Ru7aVVg2"
          :created-by-id 1
          :updated-by-id 1}]
 [:post {:id 9 :topic-id 5 :created-by-id 1 :updated-by-id 1}]
 [:post {:id 13 :topic-id 5 :created-by-id 1 :updated-by-id 1}]
 [:post {:id 17 :topic-id 5 :created-by-id 1 :updated-by-id 1}]]


(insert {:like [[3]]})
@mock-db
[[:user {:id 1 :username "T2TD3pAB79X5"}]
 [:user {:id 2 :username "ziJ9GnvNMOHcaUz"}]
 [:topic-category {:id 3 :created-by-id 2 :updated-by-id 2}]
 [:topic {:id 6
          :topic-category-id 3
          :title "4juV71q9Ih9eE1"
          :created-by-id 2
          :updated-by-id 2}]
 [:post {:id 10 :topic-id 6 :created-by-id 2 :updated-by-id 2}]
 [:like {:id 14 :post-id 10 :created-by-id 1}]
 [:like {:id 17 :post-id 10 :created-by-id 2}]
 [:user {:id 20 :username "b73Ts5BoO"}]
 [:like {:id 21 :post-id 10 :created-by-id 20}]]


(insert {:polymorphic-like [[3 {:ref-types {:liked-id :post}}]]})
@mock-db
[[:user {:id 1 :username "gI3q3Y6HR1uwc"}]
 [:user {:id 2 :username "klKs7"}]
 [:topic-category {:id 3 :created-by-id 2 :updated-by-id 2}]
 [:topic {:id 6
          :topic-category-id 3
          :title "RF6g"
          :created-by-id 2
          :updated-by-id 2}]
 [:post {:id 10 :topic-id 6 :created-by-id 2 :updated-by-id 2}]
 [:polymorphic-like {:id 14 :liked-id 10 :created-by-id 1}]
 [:polymorphic-like {:id 17 :liked-id 10 :created-by-id 2}]
 [:user {:id 20 :username "Gcf"}]
 [:polymorphic-like {:id 21 :liked-id 10 :created-by-id 20}]]


(insert {:polymorphic-like [[3 {:ref-types {:liked-id :topic}}]]})
@mock-db
[[:user {:id 1 :username "5Z382YCNrJB"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:topic {:id 5
          :topic-category-id 2
          :title "i3"
          :created-by-id 1
          :updated-by-id 1}]
 [:user {:id 9 :username "dJtC"}]
 [:polymorphic-like {:id 10 :liked-id 5 :created-by-id 9}]
 [:polymorphic-like {:id 13 :liked-id 5 :created-by-id 1}]
 [:user {:id 16 :username "8ZS"}]
 [:polymorphic-like {:id 17 :liked-id 5 :created-by-id 16}]]
