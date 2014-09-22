(ns clj-parasoup.static-content.service
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.static-content.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol StaticContentService
  (respond [this response-channel request]))

(trapperkeeper/defservice
  static-content-service
  StaticContentService
  []
  (respond [this response-channel request]
           (core/respond response-channel request)))
