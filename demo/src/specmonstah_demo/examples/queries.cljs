(ns specmonstah-demo.examples.queries
  (:require [shadow.resource :as rc]))

(def queries
  [{:name        "specify usernames"
    :description "Specmonstah generates a lot of data for you, but you have the power to customize any of it."
    :query       {:user [[:custom-user-1 {:spec-gen {:user-name "Captain Crunch"}}]
                         [:custom-user-2 {:spec-gen {:id 100}}]]}}
   {:name        "multiple ents"
    :description "Generate 3 todos. Try generating more or fewer."
    :query       {:todo [[3]]}}
   {:name        ":coll constraint"
    :description "Projects store multiple todo list ids under :todo-list-ids."
    :query       {:project [[:_ {:refs {:todo-list-ids 3}}]]}}
   {:name        ":uniq constraint"
    :description "Users can't watch the same todo list twice. Therefore, when generating todo list watches, also generate unique todo lists to watch."
    :query       {:todo-list-watch [[3]]}}])
