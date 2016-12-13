(set-env!
  :source-paths   #{"src" "test"}
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
  pom  {:project     'reifyhealth/specmonstah
        :version     +version+
        :description "Generate and process records forming a DAG"
        :url         "https://github.com/reifyhealth/specmonstah"
        :scm         {:url "https://github.com/reifyhealth/specmonstah"}
        :license     {"MIT" "https://opensource.org/licenses/MIT"}})

(deftask make
  "build a jar"
  []
  (comp (pom)
        (jar :file (str "specmonsah-" +version+ ".jar"))
        (target :dir #{"target/build"})))

(deftask push-release-without-gpg
  "Deploy release version to Clojars without gpg signature."
  [f file PATH str "The jar file to deploy."]
  (comp
   (#'adzerk.bootlaces/collect-clojars-credentials)
   (push
    :file           file
    :tag            (boolean #'adzerk.bootlaces/+last-commit+)
    :gpg-sign       false
    :ensure-release true
    :repo           "deploy-clojars")))