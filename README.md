<img align="right" src="docs/small-monstahs.png">

# Specmonstah

* [Short Sweet Example](https://sweet-tooth.gitbook.io/specmonstah/#short-sweet-example)
* [Infomercial](https://sweet-tooth.gitbook.io/specmonstah/infomercial)
* [Tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial)
* [Interactive Demo](https://reifyhealth.github.io/specmonstah/)

## Deps

```clojure
[reifyhealth/specmonstah "2.0.0-alpha-2"]
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

[Check out the
tutorial](https://sweet-tooth.gitbook.io/specmonstah/tutorial) to
learn how to use Specmonstah :)
