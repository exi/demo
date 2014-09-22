(ns clj-parasoup.static-content.core
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.static-content.templates :as tmpl]))

(defn template->string [template] (apply str (template)))

(def ending->type-map {"js" "application/javascript"
                       "map" "application/json"})

(defn file-ending
  [file-path]
  (last (re-find #"\.(\w+)$" file-path)))

(defn content-type [file-path]
  (ending->type-map (file-ending file-path)))

(defn create-response [status body headers]
  {:status status
   :body body
   :headers headers})

(defn with-200 [body headers]
  (create-response
   200
   body
   headers))

(defn with-index []
  (with-200
    (template->string tmpl/index)
    {:content-type "text/html"}))

(defn with-file [file-path]
  (with-200
    (slurp file-path)
    {:content-type (content-type file-path)}))

(defn with-404 []
  (create-response 404 "Not found" {}))

(defn with-static [request]
  (let [file-path (str "static" (:uri request))]
    (if (.exists (clojure.java.io/as-file file-path))
      (with-file file-path)
      (with-404))))

(defn respond [request]
  (log/debug "respond with static content")
  (cond
   (= "/" (:uri request)) (with-index)
   (re-find #"^/" (:uri request)) (with-static request)
   :else (with-404)))
