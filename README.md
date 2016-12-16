# Specmonstah

Specmonstah makes it easy to generate and operate on the kind of
directed acyclic graph (DAG) we encounter often when, for example,
inserting fixture records in a relational database.

## Brief Example

The following examples won't actually run if you copy and paste them,
they're just meant to convey Specmonstah's purpose. If you're a _just
show me some code!_ kind of person, check out
[examples/reify/specmonstah_examples.clj](examples/reify/specmonstah_examples.clj).

Say you want to test what happens when you call an `insert!` function
on a _chapter_ record, but before you do that you have to write ugly
stupid annoying boilerplate to insert a _book_ and a _publisher_ and
an _address_ because of foreign key constraints. Gross! Your gross
code might look something like this:

```
(let [address (insert! :author {:street "10 Chestnut St" :id 10})
      publisher (insert! :publisher {:name "Publishy" :id 20 :address-id 10})
      book (insert! :book {:name "Booky Bookingsly" :id 30 :publisher-id 20})
      chapter-data {:name "Chapter Chaptersly" :id 40 :book-id 30}]
  (is (= (insert! :chapter chapter-data)
         chapter-data)))
```

That's a lot of setup that's not directly related to what you're
trying to test. You also have to be careful to make sure that the
_publisher'_ `:address-id` corresponds to the actual `:id` of the
_address_, making your code brittle.

With specmonstah, your code can look like this:

```
(let [tree (specmonstah/dotree insert! gen1 relations [::chapter])
      chapter-data (get-in tree [::specmonstah/query 0])]
  (is (= (insert! :chapter chapter-data)
         chapter-data)))
```

If you've defined `insert!`, `gen1`, and `relations`, and you've
defined the `::chapter` spec, then this code will insert _address_,
_book_ and _publisher_ records where all foreign keys are
correct. `chapter-data`'s `:book-id` will be the id of the book that
was created.

Now imagine you want to work with two chapters, where each chapter
belongs to a separate book, and each book has a separate publisher and
separate address. Specmonstah lets you express this scenario using a
compact DSL:

```
(specmonstah/dotree
  insert! gen1 relations
  [::chapter [::chapter {:book-id [:b1 {:publisher-id [:p1 {:address-id :a1}]}]}]])
```

Of course the DSL looks like gibberish to you now, but hopefully it
conveys how _cool_ and _fun_ and _not gross_ it is compared to having
to explicitly write boilerplate insert statements for every book,
publisher, and address.

Let's fully learn how to use Specmonstah.

## Tutorial

To use Specmonstah, you define:

* A _generator function_ that takes one argument and returns the
  different kinds of maps your system works with. For example, if your
  function is `gen1` then `(gen1 ::book)` should return a book
  map. (Specmonstah was made with
  [clojure.spec](http://clojure.org/guides/spec) in mind, and we'll
  use it in the upcoming examples, but it's not strictly necessary.)
* A _relations map_ that defines how entities reference each other;
  for example, that a book's `:publisher-id` refers to a publisher's
  `:id`
* A _query_ that specifies which entities you're interested in working

Let's start with a simple example. In this example, we're going to
focus only on _generating_ data structures; we're not going to insert
them into a database.

```clojure
(ns reify.specmontah-examples
  (:require [reify.specmonstah.core :as rs]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(s/def ::id pos-int?)

(s/def ::publisher-name #{"Deault Publisher Name"})
(s/def ::publisher (s/keys :req-un [::id ::publisher-name]))

(s/def ::book-name #{"Default Book Name"})
(s/def ::book (s/keys :req-un [::id ::book-name]))

(s/def ::chapter-name #{"Default Chapter Title"})
(s/def ::chapter (s/keys :req-un [::id ::chapter-name]))

(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(def relations
  (rs/expand-relation-template
    {::publisher [{}]
     ::book [{:publisher-id [::publisher :id]}]
     ::chapter [{:book-id [::book :id]}]}))

(def result-1 (rs/gen-tree gen1 relations [::chapter]))
```

Here, we generate maps by calling `gen1` with `::book`, `::chapter`,
or `::publisher`:

```clojure
(gen1 ::book)
; => {:id 94, :book-name "Default Book Name"}

(gen1 ::chapter)
; => {:id 59, :chapter-name "Default Chapter Title"}
```

We create a relations map with this bit:

```
(def relations
  (rs/expand-relation-template
    {::publisher [{}]
     ::book [{:publisher-id [::publisher :id]}]
     ::chapter [{:book-id [::book :id]}]}))
```

This lets you say that `::book` maps have a `:publisher-id`, and in
order to set that `:publisher-id`, Specmonstah should generate a
`::publisher` and use the value of its `:id`. Likewise with `::chapters`
and their `:book-id`.

Let's look at this in action:

```clojure
(def result-1 (rs/gen-tree gen1 relations [::chapter]))
result-1
; =>
{::publisher {::rs/template {:id 1, :publisher-name "Deault Publisher Name"}}
 ::book {::rs/template {:id 192108, :book-name "Default Book Name", :publisher-id 1}}
 ::rs/query [[::rs/chapter {:id 213, :chapter-name "Default Chapter Title", :book-id 192108}]]
 ::rs/order [[::publisher ::rs/template]
             [::book ::rs/template]]}
```

`rs/gen-tree` takes three arguments: the generating function,
relations, and a query, and it returns a map.

The query is `[::chapter]`. This tells `gen-tree`, "Generate the
entire graph of entities that must exist for a `::chapter` to
exist. Our `relations` maps specifies that a chapter refers to a book,
and a book refers to a publisher. And indeed, the map returned by `gen-tree`
has a publisher and a book, which we can access like so:

```clojure
(get-in result-1 [::publisher ::rs/template])
; => {:id 1, :publisher-name "Deault Publisher Name"}

(get-in result-1 [::book ::rs/template])
; => {:id 192108, :book-name "Default Book Name", :publisher-id 1}
```

(I'll explain this `::rs/template` business later.)

Notice that the book's `:publisher-id` is `1`, which is the
publisher's `:id`.

`rs/gen-tree`'s return value also includes entities generated for each
query term under `::rs/query`:

```clojure
(::rs/query result-1)
; =>
[[::rs/chapter {:id 213, :chapter-name "Default Chapter Title", :book-id 192108}]]
```

Notice that the `:book-id` is correct.

`rs/gen-tree` takes a generator function, relations, and a query, and
generates the entire graph of entities that your query term depends
on. It also generates an entity for your query term. Here, it's like
you're saying, "I need to work with a _::chapter_, but it has
dependencies that must exist, so please generate those dependencies
and make sure that the _::chapter_ points to them."

That's the heart of what Specmonstah does, but it can get a lot more
sophisticated. Let's look at more complex and interesting queries.

## More Query Features

### Multiple query terms

You can have more than one query term:

```clojure
(def result-2 (rs/gen-tree gen1 relations [::chapter ::book]))
result-2
; => 
{::publisher {::rs/template {:id 9365, :publisher-name "Deault Publisher Name"}}
 ::book {::rs/template {:id 6119, :book-name "Default Book Name", :publisher-id 9365}}
 ::rs/query [[::chapter {:id 69189760, :chapter-name "Default Chapter Title", :book-id 6119}]
             [::book {:id 6938682, :book-name "Default Book Name", :publisher-id 9365}]]
 ::rs/order ([::publisher ::rs/template]
             [::book ::rs/template])}
```

Here, your query is `[::chapter ::book]`. You can see that a publisher
has been generated at `(get-in result-2 [::publisher ::rs/template])`,
and a book has been generated at `(get-in result-2
[::book ::rs/template])`, just like before. Here's what's different:

```clojure
(::rs/query result-2)
; => 
[[::chapter {:id 69189760, :chapter-name "Default Chapter Title", :book-id 6119}]
 [::book {:id 6938682, :book-name "Default Book Name", :publisher-id 9365}]]
```

This time, there are two values: a _::chapter_ and a _::book_,
corresponding to our query terms (query term order is
preserved). Notice that the `:publisher-id` is `9365` for both the
book under `::rs/query` and under `(get-in result-2
[::book ::rs/template])`. The chapter's `:book-id` refers to the book
at `(get-in result-2 [::book ::rs/template])`, _not_ the book under
`::rs/query`.

### Specifying Relationships

What if you want to work with two books that belong to different
publishers? You could do that like this:

```clojure
(def result-3 (rs/gen-tree gen1 relations [::book [::book {:publisher-id :p1}]]))
result-3
; =>
{::publisher {::rs/template {:id 1002, :publisher-name "Deault Publisher Name"}
              :p1 {:id 14419, :publisher-name "Deault Publisher Name"}}
 ::rs/query [[::book {:id 1, :book-name "Default Book Name", :publisher-id 1002}]
             [::book {:id 10, :book-name "Default Book Name", :publisher-id 14419}]]
 ::order ([::publisher ::rs/template]
          [::publisher :p1])}
```

Under `::publisher`, there are now two keys: `::rs/template` and
`:p1`. `:p1` is taken from the query term
`[::book {:publisher-id :p1}]`.

This query term says, "generate a book,
but instead of getting its `:publisher-id` from the `::publisher`
named `::rs/template`, create a new publisher named `:p1` and get the
`:publisher-id` from that.

Extended query terms like `[::book {:publisher-id :p1}]` are vectors
instead of just a keyword. The first element should be an entity type:
`::book`, here. The second element should be a map where the keys
"foreign keys" (`:publisher-id`) and the value is some arbitrary name:
`:p1` in this case, but `:pub1` or `:foobity` will work too.

You can use these names so that multiple query terms can reference the
same entity:

```clojure
(def result-4 (rs/gen-tree gen1 relations [::book
                                           [::book {:publisher-id :p1}]
                                           [::book {:publisher-id :p1}]]))
result-4
; => 
```

This generates three books. The second and third will have the same
`:publisher-id`, and the first will have a different `:publisher-id`.

### Specifying Nested Relationships

Check this out:

```clojure
(def result-5 (rs/gen-tree gen1 relations [::chapter
                                           [::chapter {:book-id [:b1 {:publisher-id :p1}]}]]))
```

This says, "Create two chapters. The second chapter belongs to a
different book from the first chapter, and that book has a different
publisher."

### Specifying Attributes

You can merge your map in to the generated maps:

```clojure
(def result-6 (rs/gen-tree gen1 relations [[::chapter {} {:chapter-name "Custom Chapter Name"}]
                                           [::book {:publisher-id [:p1 {} {:publisher-name "Custom Publisher Name"}]}]]))

(get-in result-6 [::rs/query 0])
; =>
[::chapter {:id 114, :chapter-name "Custom Chapter Name", :book-id 1984047}]

(get-in result-6 [::publisher :p1])
; =>
{:id 280, :publisher-name "Custom Publisher Name"}
```

## A Closer Look at Query Terms

Each extended query term can be a vector with up to three members: the
entity type, refs, and custom overrides:

```clojure
[entity-type refs custom-overrides]
```

For example, your query could be:

```clojure
[[::chapter {:book-id :b1} {:chapter-name "Custom Chapter Name"}]
 [::chapter {:book-id :b2} {:chapter-name "Other Chapter Name"}]]
```

`::chapter` would be the entity type, `{:book-id :b1}` is a ref, and
`{:chapter-name "Custom Chapter Name"}` is a map of custom overrides.

Refs are the complicated part, so let's focus on those. Refs give
names to entities that should be referenced to get a value:

```clojure
[::chapter {:book-id :b1}]
```

This means, create a `::book` named `:b1` and get this chapter's
`:book-id` from `:b1`'s `:id`.

You can also recursively specify refs:

```clojure
[::chapter {:book-id [:b1 {:publisher-id :p1}]}]
```

Here, the value for `:book-id` is no longer `:b1`, it's
`[:b1 {:publisher-id :p1}]`. This looks a lot like a query term!
Instead of the first element being an entity type, like `::book`, it's
a ref name. But the second element has the same purpose as in a query
term: it specifies another ref, `{:publisher-id :p1}`.

You can also specusify custom overrides for refs:

```clojure
[::chapter {:book-id [:b1 {:publisher-id :p1} {:book-name "Bookity Book"}]}]
```

The book named `:b1` will have a `:book-name` of `"Bookity
Book"`.

## dotree, doall

When you want to apply some function to every entity returned by
`gen-tree` in the correct order, you can use `dotree`. If you want to
apply a function to every entity _and_ every query term, you can use
`doall`.

Here's how you could use them to insert records into a database. Our
database will just be an atom:

```clojure
(def inserted-records (atom []))

(defn insert!
  [record]
  (swap! inserted-records conj record))

(rs/doall insert! gen1 relations
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

Our database `inserted-records` has had six vectors inserted into it:
three chapters, two books, and one publisher. Notice that the records
were inserted in dependency order: books reference publishers, so
publishers get inserted first. If you wanted to insert these records
in a real database with real foreign key constraints, it would work.

## License

Source Copyright © 2016 Reify Health, Inc.

Distributed under the MIT License.  See the file LICENSE.

## Credits

* @flyingmachine did the initial design and implementation with help from @khiemlam and @cldwalker
* Specmonstah relies on [loom](https://github.com/aysylu/loom)
