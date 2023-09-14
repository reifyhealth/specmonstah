<img align="right" src="docs/small-monstahs.png">

# Specmonstah

## A message from the author
> This project is no longer maintained.  If you are interested in using a maintained version we suggest that you look at the fork called [datapotato](https://github.com/donut-party/datapotato).


* [Short Sweet Example](https://sweet-tooth.gitbook.io/specmonstah/#short-sweet-example)
* [Infomercial](https://sweet-tooth.gitbook.io/specmonstah/infomercial)
* [Tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial)
* [Interactive Demo](https://reifyhealth.github.io/specmonstah/)

## Deps

```clojure
[reifyhealth/specmonstah "2.1.0"]
```

## Purpose

Specmonstah (Boston for "Specmonster") lets you write test fixtures
that are clear, concise, and easy to maintain. It's great for
dramatically reducing test boilerplate.

Say you want to test a scenario where a forum post has gotten three
likes by three different users. You'd first have to create a hierarchy
of records for the post, topic, topic category, and users. You have to
make sure that all the foreign keys are correct (e.g. the post's
`:topic-id` is set to the topic's `:id`) and that everything is inserted
in the right order.

With Specmonstah, all you have to do is **write code like this**:

```clojure
(insert {:like [[3]]})
```

and **these records get inserted** in a database (in the order
displayed):

```clojure
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
```

If you like tools that help you write code that's **clear**,
**concise**, and **easy to maintain**, then [check out the
tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial) and
learn how to use Specmonstah :)

## Short Sweet Example

If you're more of a _gimme fun now_ kind of person, then try out this
little interactive example. First, clone Specmonstah:

```
git clone https://github.com/reifyhealth/specmonstah.git
```

Open `examples/short-sweet/short_sweet.clj` in your favorite editor
and start a REPL. I've also included the code below in case for
example you don't have access to a REPL because, say, you're in some
kind of Taken situation and you only have access to a phone and you're
using your precious battery life to go through this README.

The first ~66 lines of code include all the setup necessary for the
examples to run, followed by snippets to try out with example
output. Definitely play with the snippets 😀 Can you generate multiple
todos or todo lists?

```clojure
(ns short-sweet
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

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
(s/def ::not-empty-string (s/and string? not-empty #(< (count %) 10)))

(s/def ::username ::not-empty-string)
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::created-by-id ::id)
(s/def ::content ::not-empty-string)
(s/def ::post (s/keys :req-un [::id ::created-by-id ::content]))

(s/def ::post-id ::id)
(s/def ::like (s/keys :req-un [::id ::post-id ::created-by-id]))

;; ---
;; The schema defines specmonstah `ent-types`, which roughly
;; correspond to db tables. It also defines the `:spec` for generting
;; ents of that type, and defines ent `relations` that specify how
;; ents reference each other
(def schema
  {:user {:prefix :u
          :spec   ::user}
   :post {:prefix    :p
          :spec      ::post
          :relations {:created-by-id [:user :id]}}
   :like {:prefix      :l
          :spec        ::like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})

;; Our "db" is a vector of inserted records we can use to show that
;; entities are inserted in the correct order
(def mock-db (atom []))

(defn insert*
  "Simulates inserting records in a db by conjing values onto an
  atom. ent-tye is `:user`, `:post`, or `:like`, corresponding to the
  keys in the schema. `spec-gen` is the map generated by clojure.spec"
  [{:keys [data] :as db} {:keys [ent-type spec-gen]}]
  (swap! mock-db conj [ent-type spec-gen]))

(defn insert [query]
  (reset! id-seq 0)
  (reset! mock-db [])
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

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
    (sm/attr-map :spec-gen))

;; Generating a like also generates a post and user
(-> (sg/ent-db-spec-gen {:schema schema} {:like [[1]]})
    (sm/attr-map :spec-gen))


;; The `insert` function shows that records are inserted into the
;; simulate "database" (`mock-db`) in correct dependency order:
(insert {:like [[1]]})
@mock-db
```


## Usage

This is meant as a quick reference. If none of the terms below make
sense, [check out the
tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial).

In Specmonstah, you _add ents_ to an _ent db_ using a _schema_ and
_query_. You associate ents with attributes (and perform side effects
like db insertion) using _visiting functions_.

### Schema

A schema is a map of _ent types_ to _ent type schemas_:

```clojure
;; example schema
(def schema
  {:user {:prefix :u}
   :post {:prefix :p}
   :like {:prefix      :l
          :spec        ::like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})
```

* Every ent type schema must have a `:prefix` key. This is used to
  name the ents Specmonstah generates.
* `:spec` is used by the `reifyhealth.specmonstah.spec-gen/spec-gen`
  visiting function to generate values for ents using clojure.spec
* `:relations` specify how ents of different types reference each other
* `:constraints` provide additional rules around ent generation and visitation:
  * `:uniq` means that every generated ent must reference a unique ent
    of the given type. In the schema above, multiple `:like`s must
    each reference a distinct `:user`.
  * `:coll` indicates that the given attribute can reference multiple
    ents. [See the
    tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial/11-collect-constraint-vector-of-foreign-keys)
  * `:required` is used to indicate ent sort order when your ent graph has a cycle

You can also add arbitrary keys to the schema matching the
`visit-key`s you give to visiting functions. The schema will be
available to the visiting function under the key `schema-opts`.

### Queries

You specify ents to add to an _ent db_ using a _query_:

```clojure
(sm/add-ents {:schema schema} {:like [[3]]})
```

Above, `{:like [[3]]}` is a query meaning "Add 3 likes to the ent db,
as well as the hierarchy of ents necessary for 3 likes to be present."

When you add ents to the ent db, that means that Specmonstah has
created a graph node to represent the ent and added it an internal
graph that represents all their ents and their relationships.

### Visiting functions

You can apply a function to each ent's graph node in topologically
sorted (topsort) order and associate the return value as a node
attribute.

(Topsort means that if a `:post` references a `:user`, then the
`:user` will be placed before the `:post` in the sort.)

```clojure
(-> (sm/add-ents {:schema good-schema} {:like [[3]]})
    (sm/visit-ents :prn (fn [db {:keys [ent-name ent-type]}]
                          (prn [ent-name ent-type]))))
[:u1 :user]
[:p0 :post]
[:l1 :like]
[:u0 :user]
[:l0 :like]
[:u2 :user]
[:l2 :like]
```

In the example above, `sm/visit-ents` is used to apply an anonymous
function to every ent, printing the ent's name and type. The `:prn`
key is called the _visit key_. The return value of the visiting
function is associated with each ent node using the visit key.

The first argument to the visit function is always the entire ent
db. The second argument is a map that includes the following keys:

* `:ent-name`: `:u0`, `:u1` and the like
* `:attrs`: a map of all node attrs for the ent. These attrs are also
  merged into the map passed to the visit function
* `:visit-val` - current value of the visit attr for this node. Could
  be present from previous visits.
* `:visit-key`, the key used to associate the return value of the
  visit fn with the node
* `:query-opts`: any options you might have included in the query used
  to generate this node
* `:visit-query-opts`: just looks up the value of `:visit-key` in the
  `:query-opts` map
* `:schema-opts`: any options set for `:visit-key` in the schema

## TODO

- document `:bind` syntax
- document `wrap-gen-data-visiting-fn`
