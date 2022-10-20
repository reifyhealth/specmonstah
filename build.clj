(ns build
  "specmonstah's build script. inspired by:
  * https://github.com/seancorfield/honeysql/blob/develop/build.clj
  * https://github.com/seancorfield/build-clj

  Run tests:
  clojure -X:test
  clojure -X:test-cljs
  For more information, run:
  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'reifyhealth/specmonstah)
(def version (format "2.2.0" (b/git-count-revs nil)))

(defn deploy "Deploy the JAR to Clojars"
  [opts]
  (if-not (System/getenv "CI")
    (do (println "Only CI is allowed to push a release")
        (System/exit 1))
    (-> opts
        (assoc :lib lib :version version)
        (bb/deploy))))

(defn jar "build a jar"
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn test "Run basic tests." [opts]
  (-> opts
      (bb/run-tests)))
