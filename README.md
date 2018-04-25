# Specmonstah

* [Purpose](#purpose)
* [Tutorial](#tutorial)
* [Usage](#usage)
* [Glossary](#glossary)

## Purpose

Specmonstah lets you generate and manipulate deeply-nested,
hierarchical graphs of business data (what you typically store in a
relational database) using a concise DSL. It's great for dramatically
reducing the amount of boilerplate code you have to write for tests.

![Specmonstah purpose](docs/diagram.png)

For example, say you need to test the insertion of a few _Todos_ in a
database: your foreign key constraints would require you to first
insert the _TodoList_ that the to Todo belongs to, and the _User_ that
the TodoList and Todo belong to. You could use the following to
generate the graph:

```clojure
(ns your-project.core
  (:require [reifyhealth.specmonstah.core :as sm]
            [loom.io :as lio]))

;; The schema is similar to a db schema. It's used to establish
;; entity types and relationships among instances of those entities.
(def schema
  {:user      {:prefix :u}
   :todo      {:relations {:created-by-id [:user :id]
                           :updated-by-id [:user :id]
                           :todo-list-id  [:todo-list :id]}
               :prefix    :t}
   :todo-list {:relations {:created-by-id [:user :id]
                           :updated-by-id [:user :id]}
               :prefix    :tl}})

;; The graph is under `:data`, and `lio/view` produces an image
;; of the graph.
(-> (sm/build-ent-db {:schema schema} {:todo [3]})
    :data
    lio/view)
```

![Simple todo example](docs/todo-example.png)

This graph shows that you've generated three Todos which belong to a
single TodoList. The Todos and TodoList both are both related to a
User. In the line `(sm/build-ent-db {:schema schema} {:todo [3]})`,
you're using the _query_ `{:todo [3]}` to tell the function
`sm/build-ent-db` to generate three Todos. `sm/build-ent-db` uses
`schema` to generate `User` and `TodoList` entities without your
having to specify them in your query.

The graph only contains entity types, and entity instances and their
relationships; it doesn't include fields for the entities like the
user's name or the todo list's name. Once you've generated the graph,
it's straightforward to visit each node in the graph to a) use
clojure spec to generate that data and b) insert the generated data
into a database.

Specmonstah was born out of a need to replace brittle, repetitive code
for creating deeply-nested hierarchies of data in unit tests. This
guide's tutorial will show you how to use Specmonstah specifically for
this use case. Along the way you'll learn how to make the most of
Specmonstah by understanding how it's not implemented to support
writing unit tests per se, but to support the more fundamental
operations of generating and manipulating entity graphs.

## Tutorial

## Usage

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
