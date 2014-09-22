(ns clj-parasoup.core
  (:gen-class)
  (:require [puppetlabs.trapperkeeper.core :as tkc]))

(defn -main [& args]
  (apply
   (resolve 'puppetlabs.trapperkeeper.core/main)
   args))

(defn start []
  (let [args ["--config" "resources/config.ini" "--bootstrap-config" "resources/bootstrap.cfg"]]
    (future
     (apply
      (resolve 'puppetlabs.trapperkeeper.core/main)
      args))))
