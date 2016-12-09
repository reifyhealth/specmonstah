# Specmonstah

## The Hook

This code, which you can find in
[examples/reify/specmonstah_examples.clj](examples/reify/specmonstah_examples.clj),
shows how you can use Specmonstah to generate and operate on a
graph. In the example, you create and "insert" (update an atom) three
_chapters_. Chapters reference _books_, and books reference
_publishers_.

```clojure
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
  
; =>
[[::publisher {:id 1, :publisher-name "Deault Publisher Name"}]
 [::book {:id 2, :book-name "Custom Book Name", :publisher-id 1}]
 [::book {:id 3, :book-name "Default Book Name", :publisher-id 1}]
 [::chapter {:id 4, :chapter-name "Default Chapter Title", :book-id 3}]
 [::chapter {:id 5, :chapter-name "Default Chapter Title", :book-id 3}]
 [::chapter {:id 6, :chapter-name "Custom Chapter Name", :book-id 2}]]
```

Our database `inserted-records` has had six vectors inserted into
it. Notice that both book records have a `:publisher-id` of `1`, which
is in fact the `:id` of the only publisher record. Notice also that
we've specified a custom name for one of the books, `"Custom Book
Name"`. Among the chapters, two have the same default name and refer
to the same book (`:book-id 3`), and the last has a custom name and
refers to a different book (`:book-id 2`).

Finally, notice that the records were inserted in dependency order:
books reference publishers, so publishers get inserted first. If you
wanted to insert these records in a real database with real foreign
key constraints, it would work.

If this is the kind of thing that speeds the tempo of your sweet
little heart, read on!

## Introduction

Hi. Let's talk about why you're here. You're here because you're
writing a lot of boring repetitive boilerplate code when dealing with,
essentially, graphs. You want some generic way to generate these
graphs, preferably with [clojure.spec](http://clojure.org/about/spec)
because it's way cool.

For example, You're testing some database interaction like, "when I
insert this _chapter_ record into the _chapters_ table, then the row
count should increase by 1 and the return value should be a _chapter_
map." The problem is, your _chapters_ table has a foreign key
constraint so it requires a `book-id` that refers to an actual _book_
record, and the _books_ table similarly refers to a _publisher_. You
write code that looks something like this pseudocode:

```clojure
(ns your.db-test)

(let [publisher (insert! ::publisher {:id 1
                                      :publisher-name "Bookity Books"})
      book (insert! ::book {:id 5
                            :book-name "Immutability: Fact or Fiction?"
                            :publisher-id 1})
      chapter-count (table-count :chapter)
      chapter (insert! ::chapter {:id 20
                                  :chapter-name "History: The Unchanging Past"
                                  :book-id 5})]
  (do-something-with chapter))
```

You only care about the _chapter_ in the _chapter -> book ->
publisher_ graph, but you have to write code for the entire thing. It
gets even worse if, for example, you need to work with two chapters
that belong to two separate books, that belong to two separate
publishers.

Now that we've seen what we don't want, let's look what we do want. In
this pseudocode example, we'll see how we can define relationships
among entities once, then _query_ them to generate the dependency
graph and operate on that graph in dependency order.

```clojure
(ns your.db-test
  (:require [reify.specmonstah :as rs]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(def relations
  (rs/expand-relation-template
    {::publisher [{}]
     ::book [{:publisher-id [::publisher :id]}]
     ::chapter [{:book-id [::book :id]}]}))

(let [tree (dotree insert! #(gen/generate (s/gen %)) relations
                   [::chapter])
      chapter-data (get-in tree [::sm/query 0])
      chapter (insert! ::chapter chapter-data)]
  (do-something-with chapter))
```

This example won't actually work because we haven't defined specs for
`::publisher`, `::book`, or `::chapter`, and because we haven't
defined `insert!`. In the next section we'll look at a complete,
working example.

That said, when you use Specmonstah, you end up writing code 

With Specmonstah, you define how the nodes in your graph are related
(`relations` in the example above). You also define a function to
generate nodes in the graph (`#(gen/generate (s/gen %))`). You
specify a function to operate on the nodes (`insert!`).

Finally, you write a query: `[::chapter]`.

## Tutorial
