(ns reifyhealth.specmonstah.generate.malli
  (:require [malli.generator]
            [reifyhealth.specmonstah.generate :as generate]))

(defmethod generate/generate-entity :malli
  [_ schema]
  (malli.generator/generate schema))
