(ns specmonstah-demo.components.vendor.vis
  (:require ["react-graph-vis" :default Graph]
            [loom.graph :as lg]))

;; events at https://github.com/visjs/vis-network/blob/master/examples/network/events/interactionEvents.html

(defn sm->vis
  [{:keys [data]}]
  {:nodes (mapv (fn [n] {:id n :label (str n)}) (lg/nodes data))
   :edges (mapv (fn [[f t]] {:from f :to t}) (lg/edges data))})

(defn graph
  [db opts]
  [:> Graph (merge {:graph (sm->vis db)} opts)])
