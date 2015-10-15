(ns spark-clj-sandbox.repl
  (:require 
   [clojure.tools.nrepl.server :as nrepl-server]
   [cider.nrepl                :refer [cider-nrepl-handler]]
   [cheshire.core              :as json])
  (:gen-class
   :main true))



(defn -main [& args]
  (println "in repl.main")
  (nrepl-server/start-server :port 7888 :handler cider-nrepl-handler)
  (println "nrepl server started on 7888"))
