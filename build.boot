(set-env!
  :source-paths   #{"src"}
  :resource-paths #{}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure         "1.9.0-alpha14" :scope "provided"]
                    [boot/core                   "2.5.5"         :scope "provided"]
                    [adzerk/boot-test            "1.1.1"         :scope "test"]
                    [adzerk/bootlaces            "0.1.13"        :scope "test"]
                    [org.clojure/test.check      "0.9.0"         :scope "test"]
                    [org.clojure/tools.namespace "0.2.11"        :scope "test"]
                    [aysylu/loom "0.6.0"]
                    [medley "0.8.3"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer :all])


(def +version+ "0.1.0")
(bootlaces! +version+)

(task-options!
  pom  {:project     'com.reifyhealth/specmonstah
        :version     +version+
        :description "Generate and process records forming a DAG"
        :url         "https://github.com/reifyhealth/specmonstah"
        :scm         {:url "https://github.com/reifyhealth/specmonstah"}
        :license     {"MIT" "https://opensource.org/licenses/MIT"}})

(deftask testenv []
  (set-env! :source-paths #{"test"})
  identity)
