<img align="right" src="docs/small-monstahs.png">

# Specmonstah

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
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.generate :as generate]
            ;; Include namespace this if you want to use malli for generation
            [reifyhealth.specmonstah.generate.malli]))

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
   [:content       [:string {:gen/gen gen/string-alpha-numeric ; for readability
                             :min     1
                             :max     10}]]])

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
  (generate/generate {:schema schema} query :insert! insert*)
  ;; normally you'd return the expression above, but return nil for
  ;; the example to not produce overwhelming output
  nil)

;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; Return a map of user entities and their generated data
(-> (generate/generate {:schema schema} {:user [[3]]})
    generate/attrs)

'=> {:u0 {:id 2, :username "3O9m6"},
     :u1 {:id 1, :username "j7ABrtg0C"},
     :u2 {:id 3, :username "a"}}

;; You can specify a username and id
(-> (generate/generate {:schema schema} {:user [[1 {:set {:username "Meeghan"
                                                          :id       100}}]]})
    generate/attrs)

'=> {:u0 {:id 100, :username "Meeghan"}}

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(-> (generate/generate {:schema schema} {:post [[1]]})
    generate/attrs)

'=> {:p0 {:id 6, :created-by-id 5, :content "`"},
     :u0 {:id 5, :username "MXdL1snT"}}

;; Generating a like also generates a post and user
(-> (generate/generate {:schema schema} {:like [[1]]})
    generate/attrs)

'=> {:l0 {:id 11, :post-id 9, :created-by-id 8},
     :p0 {:id 9, :created-by-id 8, :content "qwhl2!~"},
     :u0 {:id 8, :username "2mcJPc"}}

;; The `insert` function shows that records are inserted into the
;; simulate "database" (`mock-db`) in correct dependency order:
(insert {:like [[1]]})
@mock-db

'=> [[:user {:id 1, :username "9m5VC9Us"}]
     [:post {:id 2, :created-by-id 1, :content "9Yh9KzKYAgT"}]
     [:like {:id 4, :post-id 2, :created-by-id 1}]]
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
* For an entity to be generated with `reifyhealth.specmonstah.generate/generate`
it needs to include one of the following generation mechanism:
  * `:spec` a clojure.spec for entity the same as what's used by `spec-gen`
  * `:generator` a `test.check` generator
  * `:fn` a plain function called with no arguments to generate the entity
  * `:malli` a malli schema
    * NOTE: to use malli, include
      [metosin/malli](https://github.com/metosin/malli) as a dependency on your
      project and load the namespace `reifyhealth.specmonstah.generate.malli`

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
* `:ent-type`: the type of entity being visited e.g. `:user`, `:post`
* `:ent-schema`: the schema for the entity type
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
- update tutorials to include `reifyhealth.specmonstah.generate`
