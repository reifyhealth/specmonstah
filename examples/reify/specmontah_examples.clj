(ns reify.specmontah-examples
  (:require [reify.specmonstah.core :as rs]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(def id-seq (atom 0))

(s/def ::id
  (s/with-gen
    pos-int?
    #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))

(s/def ::publisher-name #{"Deault Publisher Name"})
(s/def ::publisher (s/keys :req-un [::id ::publisher-name]))

(s/def ::book-name #{"Default Book Name"})
(s/def ::book (s/keys :req-un [::id ::book-name]))

(s/def ::chapter-name #{"Default Chapter Title"})
(s/def ::chapter (s/keys :req-un [::id ::chapter-name]))

(def relations
  (rs/expand-relation-template
    {::publisher [{}]
     ::book [{:publisher-id [::publisher :id]}]
     ::chapter [{:book-id [::book :id]}]}))

(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(def inserted-records (atom []))

(defn insert!
  [record]
  (swap! inserted-records conj record))

(rs/doall insert! #(gen/generate (s/gen %)) relations
          [[::chapter]
           [::chapter]
           [::chapter {:book-id [:b1 {} {:book-name "Custom Book Name"}]}
                      {:chapter-name "Custom Chapter Name"}]])
@inserted-records
