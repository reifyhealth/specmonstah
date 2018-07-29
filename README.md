# Specmonstah

* [Introduction](#introduction)
* [Infomercial](#infomercial)
* [Tutorial](#tutorial)
* [Usage Reference](#usage-reference)
* [Glossary](#glossary)

## Introduction

Specmonstah lets you generate and manipulate deeply-nested,
hierarchical graphs of business data (what you typically store in a
relational database) using a concise DSL. It's great for dramatically
reducing the amount of boilerplate code you have to write for
tests. It's similar in purpose to Ruby's
[factory_bot](https://github.com/thoughtbot/factory_bot).

![Specmonstah purpose](docs/diagram.png)

For example, say you need to test a function that inserts a _Todo_ in
a database: your foreign key constraints would require you to first
insert the _TodoList_ that the Todo belongs to, and the _User_ that
the TodoList and Todo belong to. Instead of having to write something
like this:

```clojure
(let [user      (create-user! {:username "bob" :email "bob@bob-town.com"})
      todo-list (create-todo-list! {:title         "Bob convention todos"
                                    :created-by-id (:id user)})]
  (create-todo! {:description "Book flight"
                 :todo-list-id (:id todo-list)
                 :created-by-id (:id user)}))
```

Specmonstah lets you write something like this:

```clojure
(create! {:todo [[1 {:spec-gen {:description "Book flight"}}]]})
```

Specmonstah creates the User and TodoList, and ensures that the
TodoList correctly references the User and the Todo correctly
references the TodoList and user. Call me crazy, but I think the
second snippet is preferable to the first.

Specmonstah is a specialized tool that introduces new concepts and
vocabulary. It will take an hour or two to get comfortable with it,
but once you do, that investment will pay huge dividends over time as
you use it to write code that is more clear, concise, and easy to
maintain. This guide has four parts to get you there:

* The [Infomercial](#infomercial) is a quick tour of the cool stuff you can
  do with Specmonstah, showing you why it's worth the investment
* A detailed [Tutorial](#tutorial) introduces you to Specmonstah
  concepts one a time 
* The [Usage Reference](#usage-reference) contains code snippets
  demonstrating the facets of Specmonstah usage
* Because Specmonstah introduces new terms, we provide a
  [Glossary](#glossary)

## Infomercial

In the time-honored tradition of infomercials everywhere, these
snippets gloss over a lot of details to reveal the truest, purest
essence of a product. If you want to go all FDA on me and validate the
claims, check out [the full
source](./examples/reifyhealth/infomercial.cljc).

The code below will ~~shout at~~ show you how you can generate and
insert data for a forum's database. Here's an entity relationship
diagram for the database:

![Forum ERD](docs/forum-erd.png)

One thing the diagram doesn't capture is that, for the `like` type,
there's a uniqueness constraint on `post-id` and
`created-by-id`. Also, every instance of `created-by-id` and
`updated-by-id` refers to a user, but including that in the diagram
would just clutter it.

### Insert entity hierarchy in dependency order, with correct foreign keys

Posts have a foreign key referencing a Topic, Topics reference a Topic
Category, and all of these reference a User. The snippet below shows
that you can specify that you want one Post created, and Specmonstah
will ensure that the other entities are created and inserted in
dependency order:

```clojure
(insert {:post [[1]]})
@ent-db
; =>
[[:user {:id 1 :username "K7X5r6UVs9Mm2Eks"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:topic {:id 5
          :topic-category-id 2
          :title "ejJ2B88UZo2NK2sMuU4"
          :created-by-id 1
          :updated-by-id 1}]
 [:post {:id 9 :topic-id 5 :created-by-id 1 :updated-by-id 1}]]
```

The `insert` function simulates inserting records in a db by conjing
entities on the `ent-db` atom. The maps were generated using
`clojure.spec`. Notice that all the foreign keys line up.

### Specify different users

In the previous example, all entities referenced the same User. In
this one, the Topic's `created-by-id` will reference a new user:

```clojure
(insert {:topic [[:t0 {:refs {:created-by-id :custom-user}}]]
         :post [[1]]})
@ent-db
; =>
[[:user {:id 1 :username "gMKGTwBnOvB0xt"}]
 [:topic-category {:id 2 :created-by-id 1 :updated-by-id 1}]
 [:user {:id 5 :username "2jK0TXCU2UcBM89"}]
 [:topic {:id 6
          :topic-category-id 2
          :title "cmo2Vg8DQByz302c"
          :created-by-id 5
          :updated-by-id 1}]
 [:post {:id 10 :topic-id 6 :created-by-id 1 :updated-by-id 1}]]
```

Two users, one with `:id 1` and another with `:id 5`. The topic's
`:created-by-id` attribute is 5, and all other User references are
`1`.

### Multiple entities

What if you want to insert 2 or 3 or more posts?

```clojure
(insert {:post [[3]]})
@ent-db
; =>
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
```

Just say "I want 3 posts" and Specmonstah delivers.

### Uniqueness constraints

You can't have two Likes that reference the same Post and User; in
other words, a User can't Like the same Post twice. Specmonstah will
automatically generate unique Users if you specify multiple Likes:

```clojure
(insert {:like [[3]]})
@ent-db
; =>
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

Three Likes, Three different Users, and we're not violating the
uniqueness constraaint. With just one line of code. I think this
feature's particularly cool.

### Polymorphic relations

Whereas foreign keys in RDBMSs must reference records in a specific
table, some databases like Datomic have reference types attributes
that can reference any entity at all. You might want to use this in
your forum so that users can like either Topics or Posts. Specmonstah
handles this use case.

There are two snippets below. In teh first, you say you want to create
three `:polymorphic-like`s with `{:ref-types {:liked-id
:post}}`. Specmonstah generates 3 likes that refer to a post. The
second snippet includes `{:ref-types {:liked-id :topic}}`, so the
likes refer to a topic. Polymorphic references compose with uniqueness
contraints, so three users are created, just like in the previous snippet.

```clojure
(insert {:polymorphic-like [[3 {:ref-types {:liked-id :post}}]]})
@ent-db
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
@ent-db
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
```


### Visualization

Sometimes you want to inspect aall the work that Specmonstah is doing
for you. One way to do that is to produce an image of the entities
Specmonstah produces, and their relationships:

```clojure
(lio/view (:data (sm/build-ent-db {:schema schema} {:like [[2]]})))
```

![like graph](docs/like-graph.png)

This shows that that two Likes were generated (`l0` and `l1`). The
Likes are applied to a Post (`p0`), and so forth.

And that brings the infomercial to a close. If you're ready to learn
how you, too, can accomplish these amazing feats, read on!

## Tutorial

Specmonstah was born out of a need to replace brittle, repetitive code
for creating deeply-nested hierarchies of data in unit tests. This
tutorial will show you how to use Specmonstah specifically for this
use case. Along the way you'll learn how to make the most of
Specmonstah by understanding how it's not implemented to support
writing unit tests per se, but to support the more fundamental
operations of generating and manipulating entity graphs.

In learning any new tool, I think it's useful to begin by learning the
tool's purpose, then getting a high-level overview of the architecture
and how it achieves the tool's purpose. With those concepts in place,
concrete examples and exercises will help you understand how to use
the tool. If you think that approach is bazonkers because you're a
hands-on type purpose, you can skip to [01: schema, query,
ent-db](#01-schema-query-ent-db).

### Purpose & Architecture Overview

Specmonstah was built to aid testing by generating and inserting
records in a database in dependency order. For example, if you want to
test the insertion of a Todo, but that Todo depends on the existence
of a TodoList, and the TodoList depends on a User, then Specmonstah
will generate User and TodoList records and insert them without your
having to clutter your test with code related to Users and TodoLists.

Specmonstah accomplishes this work in three phases. The phases are
summarized here, with detailed explanations below.

1. **Graph generation.** SM uses the
   [loom](https://github.com/aysylu/loom) graph library to create a
   graph that represents the entities (ents) to insert.
2. **Ent visitation.** Once the graph is generated, you perform
   functions on each node. One function will use clojure.spec to
   generate a map of data to be inserted, and another function will
   perform the insertation.
3. **Viewing.** SM generates a lot of data, so much so that it can be
   difficult to visually parse in a REPL. SM provides functions that
   get subsets of the data for writing tests and developing in the
   REPL.

#### 1. Graph generation (and ent/ent type definitions)

It generates a graph whose nodes correspond to _entity types_ (or _ent
types_) and _entities_ (or just _ents_):

![Simple todo example](docs/todo-example.png)

In the graph above, the `:todo`, `:todo-list`, and `:user` nodes
correspond to ent types, and the rest correspond to ents.

We're going to be using the terms _ent_ and _ent type_ a lot, and
you'll see them all throughout the source code, so let's define them:

**Ent type.** An ent type analogous to a relation in a relational
database, or a class in object-oriented programming. It differs in
that relations and classes define all the attributes of their
instances, whereas ent types don't. Ent types define how instances are
related to each other. For example, the `:todo` ent type wouldn't
include a `:description` attribute, but it does specify that a `:todo`
instances reference `:todo-list` instances. In the next section you'll
learn how to define ent types.

Ent types are represented as nodes in the Specmonstah graph (let's
abbreviate that with _SG_). Ent types have directed edges to their
instances. It's rare that you'll interact with ent types directly.

**Ent.** An ent is an instance of an ent type. They have a name (`:t0,
:u0`, etc), and reference other ents. They're represented as nodes in
the SG, with directed edges going from ents to the ents they
reference; there's a directed edge from `:tl0` to `:u0` because `:tl0`
references `:u0`.

Ents can be associated with additional data - for example, a user ent
can be associated with a map of user data generated by
clojure.spec. An ent can also be associated with a value indicating
whether or not its spec data has been inserted in a database.

It's important to stress that ents themselves aren't db records, but
ents can be (and are) associated with data that ends up getting
inserted in a database. This is why we represent ents as nodes: nodes
capture the relationships among ents and serve as a flexible base
layer that we can add data to.

For example, here's the graph that's returned when you generate a
single user:

```clojure
{:nodeset #{:u0 :user}
 :adj     {:user #{:u0}}
 :in      {:u0 #{:user}}
 :attrs   {:user {:type :ent-type}
           :u0   {:type :ent :index 0 :ent-type :user :query-term [1]}}}
```

Conspicuously absent are the `:user` attributes you'd expect, like
`:username` or `:email`.

How does Specmonstah generate this graph? You'll be learning about
that in the upcoming sections.

#### 2. Ent visitation

Specmonstah provides functions for _visiting_ the ent nodes in the
graph it generates. Visiting ent nodes is kind of like mapping: when
you call `map` on a seq, you apply a mapping function to each element,
creating a new seq from the mapping function's return values. When you
visit ents, you apply a visiting function to each ent. The visiting
function's return value is stored as an attribute on the ent (remember
that ents are implemented as graph nodes, and nodes can have
attributes).

For example, there's a `spec-gen` visiting function that takes each
ent as input and uses clojure.spec to return a value. For the `:u0`
ent, whose ent type is `:user`, `spec-gen` would use clojure.spec to
return a new `:user` map, complete with name, email address, favorite
flower, whatever. That map would get assigned to the `:spec-gen`
attribute of `:u0`.

In your own application, you could implement an `insert` visiting
function that looks up the values produced by the `spec-gen` visiting
function and uses those to insert records in a database.

Below is an example graph that's been visited with the `spec-gen`
function:

```clojure
{:nodeset #{:u0 :user}
 :adj     {:user #{:u0}}
 :in      {:u0 #{:user}}
 :attrs   {:user {:type :ent-type}
           :u0   {:type     :ent :index 0 :ent-type :user :query-term [1]
                  :spec-gen {:id 2 :user-name "Luigi"}}}}
```

The last line, `:spec-gen {:id 2 :user-name "Luigi"}`, shows that the
`:u0` ent has a new attribute, `:spec-gen`, which points to a map
generated by clojure.spec.

#### 3. Viewing

When you're writing a test, it's important to be able to a) concisely
express the values you're testing and b) easily figure out why a test
is failing.

At odds with these requirements is the fact that Specmonstah returns
rich data structures under the philosophy that it's better to have
information and not need it than need it and not have it. This can be
overwhelming; picture two or three screens of output when you try to
view a raw Specmonstah value in the REPL. But fear not: Specmonstah
comes equipped with several useful _view functions_ that narrow down
its return values so that you can focus on only the information you
care about. For example, you could use the `attr-map` function to
return a map of entities and their spec-generated data. If you called
it on the data shown in the previous snippet, you'd get:

```clojure
{:u0 {:id 2 :user-name "Luigi"}}
```

So that's a bird's-eye view of Specmonstah: it's built to generate,
insert, and inspect test data. Architecturally this corresponds to
tools for generating an ent graph, visiting ents, and viewing the
slices of the result that you care about.

Now that you have the broad picture of how Specmonstah works, let's
start exploring the details with source code. The rest of the tutorial
consists of chapters with corresponding clojure files under the
[](tutorial/reifyhealth/specmonstah_tutorial) directory, each
introducing new concepts. You'll have the best experience if you
follow along in a REPL.

### Ch. 01: ent db

In this section you're going to learn about the _ent db_. Open
[reifyhealth.specmonstah-tutorial.01](tutorial/reifyhealth/specmonstah_tutorial/01.clj):

```clojure
(ns reifyhealth.specmonstah-tutorial.01
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user {:prefix :u}})

(defn ex-01
  []
  (sm/build-ent-db {:schema schema} {:user [[3]]}))
```

Throughout the tutorial, I'll use functions named `ex-01`, `ex-02`,
etc, to illustrate some concept. When you call `(ex-01)`, it returns:

```clojure
(ex-01) ;=>
{:schema {:user {:prefix :u}}
 :data {:nodeset #{:u1 :u0 :u2 :user}
        :adj {:user #{:u1 :u0 :u2}}
        :in {:u0 #{:user} :u1 #{:user} :u2 #{:user}}
        :attrs {:user {:type :ent-type}
                :u0 {:type :ent, :index 0, :ent-type :user, :query-term [3]}
                :u1 {:type :ent, :index 1, :ent-type :user, :query-term [3]}
                :u2 {:type :ent, :index 2, :ent-type :user, :query-term [3]}}}
 :queries ({:user [[3]]})
 :relation-graph {:nodeset #{:user} :adj {} :in {}}
 :types #{:user}
 :ref-ents []}
 ```

`ex-01` invokes the function call `(sm/build-ent-db {:schema schema}
{:user [[3]]})`. You'll always call `sm/build-ent-db` first whenever
you use Specmonstah. It takes two arguments, a schema and a query, and
returns an _ent db_.

ent db's are at the core of Specmonstah; most functions take an ent db
as their first argument and return an ent db. The ent db is
conceptually similar to the databases you're familiar with. Its
`:schema` key refers to an entity schema, just as an RDBMS includes
schema information. In this case, the schema is `{:user {:prefix
:u}}`, which is as simple a schema as possible. In later chapters,
you'll learn more about schemas and how they're used to define
relationships and constraints among ents.

The ent db's `:data` key refers to a
[graph](https://www.geeksforgeeks.org/graph-data-structure-and-algorithms/)
representing ents, their relationships, and their attributes. In this
ent db there are three users, `:u0`, `:u1`, and `:u2`. There aren't
any ent relationships because our schema didn't specify any, but each
ent does have attributes: `:type`, `:index`, `:ent-type`, and
`:query-term`. As you go through the tutorial, you'll see how a lot of
Specmonstah functions involve reading and updating ents' attributes.

The graph also includes nodes for ent types; you can see `:user`, and
ent-type, under the `:nodeset` key of the graph. This is used
internally.

It happens that the graph
is produced by [loom](https://github.com/aysylu/loom), a sweet little
library for working with graphs. Specmonstah doesn't try to hide this
implementation detail from you: it's entirely possible you'll want to
use one of loom's many useful graph functions to interact with the
ent db's data. You might, for instance, want to render the graph as an
image. Try this in your REPL:

```clojure
(lio/view (:data (ex-01)))
```

The rest of the keys (`:queries`, `:relation-graph`, `:types`,
`:ref-ents`) are used internally to generate the ent db, and can
safely be ignored.

Building an ent db is the first step whenever you're using
Specmonstah. The two main ingredients for building an ent db are the
_query_ and _schema_. In the next chapter, we'll explain how schemas
work, and chapter 3 will explain queries.

### Ch. 02: Schemas

Here's the source for this chapter:

```clojure
(ns reifyhealth.specmonstah-tutorial.02
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}})


(defn ex-01
  []
  (sm/build-ent-db {:schema schema} {:todo-list [[2]]}))
```

The ent db's schema defines ent types. It's implemented as a map where
keys are the ent types' names and values are their definitions. In the
code above, `schema` defines two ent types, `:user` and
`:todo-list`. The ent type definitions include two keys, `:prefix` and
`:relations`.

`:prefix` is used by `build-ent-db` to name the ents it creates. For
example, in `ex-01`, we produce an ent db that has two todo-lists and
a user:

```clojure
(defn ex-01
  []
  (sm/build-ent-db {:schema schema} {:todo-list [[2]]}))

(lio/view (:data (ex-01)))
```

![prefixes](docs/02/prefixes.png)

The todo-lists are named `:tl0` and `:tl1`, and the user is
`:u0`. There's a pattern here: every generated ent is named
`:{schema-prefix}{index}`.

The schema's `:relations` key is used to specify how ents are related
to each other. The `:todo-list` definition includes `:relations
{:owner-id [:user :id]}`, specifying that a `:todo-list` should
reference a `:user`. The relation also specifies that the
`:todo-list`'s `:owner-id` should be set to the `:user`'s `:id`,
information that will be used when we use spec-gen to generate records
for these ents.

It's because of this relation that the query `{:todo-list [[2]]}`
results in the `:user` `:u0` being created even though the query
doesn't explicitly mention `:user`, and that the `:todo-list`s
`:tl0` and `:tl1` reference `:u0`.

### Ch. 03: Queries

```clojure
(ns reifyhealth.specmonstah-tutorial.03
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

(def schema
  {:user      {:prefix :u}
   :todo-list {:prefix    :tl
               :relations {:owner-id [:user :id]}}})
(defn ex-01
  []
  (sm/build-ent-db {:schema schema} {:todo-list [[2]]}))
```


Queries are used to specify what ents should get generated. The term
_query_ might throw you off because usually it's used to refer to the
language for _retrieving_ records from a database. In Specmonstah, I
think of queries as allowing you to express, _generate the minimal
ent-db necessary for me to retrieve the ents I've specified_. I

In `ex-01`, the query passed to `sm/build-ent-db` is `{:todo-list
[[2]]}`. This is like saying, _I want two `:todo-list`s. Create an
ent-db with the minimum ents needed so that I can retrieve them._
Because `:todo-list` ents must refer to a `:user`, Specmonstah
generates the `:user` ent `:u0`. Specmonstah only generates one
`:user`, not two, because that's the mininum needed to satisfy the
query.



* numbers
* names
* options

### Ch. 04: refs

* implicit
* explicit for differentiation

### Ch. 05: spec-gen

* writing the specs
* including in the schema
* overriding in query

### Ch. 06: Custom visitors (insert)

### More use cases

* db as input
* Uniqueness constraints
* binding
* polymorphism
* collection constraint

## Usage Reference

## Glossary

## Contributing

I'm looking to exercise Specmonstah 2 against the following use cases:

* Generating data for unit tests. What to look for:
  * Are there surprises?
  * Can it handle deeply nested combinations of `:coll` and `:uniq`
    relationships?
  * Does binding work as expected?
  * Is it easy to retrieve the views of the specmonstah db needed for
    a test? For example, if you want to generate 2 Todos for
    insertion, but a TodoList and a User also get generated, can you
    access just the Todos with minimal code?
* Generating seed data.
  * Can you easily and clearly specify an entire database? For
    example, can you express "I want to create a db with 3 todo lists
    belonging to 2 users, where one list has 5 items, one has 1, and
    one has 0".
  * Can you easily tweak the above? For example if you want to create
    an additional todo list but leave everything else the same, or
    generate only empty todo lists.
* Progressively generating and mapping a database.
  * Does anything unexpected happen if you create an entity database
    and map over it to perform inserts over multiple calls?

## Notes to self

* Tutorial is structured so that dev can start using SM ASAP, within
  first few chapters, and the remaining chapters fill in details as
  needed.
