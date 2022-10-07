(set-env!
  :source-paths   #{"src" "test"}
  :resource-paths #{}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure         "1.10.0"        :scope "provided"]
                    [org.clojure/clojurescript   "1.10.516"      :scope "provided"]
                    [boot/core                   "2.5.5"         :scope "provided"]
                    [adzerk/boot-test            "1.1.1"         :scope "test"]
                    [adzerk/bootlaces            "0.1.13"        :scope "test"]
                    [org.clojure/test.check      "0.9.0"         :scope "test"]
                    [org.clojure/tools.namespace "0.2.11"        :scope "test"]
                    [adzerk/boot-cljs            "2.0.0"         :scope "test"]
                    [crisptrutski/boot-cljs-test "0.3.0"         :scope "test"]
                    [doo                         "0.1.7"         :scope "test"]
                    [org.clojure/spec.alpha      "0.1.123"       :scope "test"]
                    [better-cond                 "2.0.1-SNAPSHOT"]
                    [aysylu/loom                 "1.0.2"]
                    [medley                      "0.8.3"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer :all]
         '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(def +version+ "2.1.0")
(bootlaces! +version+)

(task-options!
  pom  {:project     'reifyhealth/specmonstah
        :version     +version+
        :description "Generate and process graphs of dependencies"
        :url         "https://github.com/reifyhealth/specmonstah"
        :scm         {:url "https://github.com/reifyhealth/specmonstah"}
        :license     {"MIT" "https://opensource.org/licenses/MIT"}}
  test-cljs {:js-env :node})

(deftask test-all
  "unit tests and cljs tests"
  []
  (comp (test)
        (test-cljs :exit? true)))

(deftask make
  "build a jar"
  []
  (comp (pom)
        (jar :file (str "specmonstah-" +version+ ".jar"))
        (target :dir #{"target/build"})))

(deftask push-release-without-gpg
  "Deploy release version to Clojars without gpg signature."
  [f file PATH str "The jar file to deploy."]
  (if (System/getenv "CI")
    (comp
      (#'adzerk.bootlaces/collect-clojars-credentials)
      (push
        :file           file
        :tag            (boolean #'adzerk.bootlaces/+last-commit+)
        :gpg-sign       false
        :ensure-release true
        :repo           "deploy-clojars"))
    (do (println "Only CI is allowed to push a release")
        (System/exit 1))))
