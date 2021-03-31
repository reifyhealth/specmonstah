(ns test-runner
  (:require [clojure.test :as t]
            [spartan.spec]
            [reifyhealth.specmonstah.core-test]
            ;; spartan doesn't support generators yet so spec-gen ns
            ;; not supported yet. With spartan got stuck on s/gen
            #_[reifyhealth.specmonstah.spec-gen-test]))

(defn -main []
  (let [{:keys [:fail :error]} (t/run-all-tests #".*-test")]
    (System/exit (+ fail error))))
