(ns clj-parasoup.database.dummy.service
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.database.protocol :as dbp]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]))

(trapperkeeper/defservice
  dummy-database-service
  dbp/DatabaseService
  []
  (start [this context]
         (assoc context
           :token (atom {})
           :file (atom {})
           :gfy (atom {})))
  (put-file [this file-name byte-data content-type]
            (swap! (:file (service-context this))
                   assoc file-name {:byte-data byte-data
                                    :content-type content-type}))
  (get-file [this file-name]
            (get @(:file (service-context this))
                 file-name))
  (get-gfy [this file-name]
           (get @(:gfy (service-context this))
                file-name))
  (put-gfy [this file-name gfy-name]
           (swap! (:gfy (service-context this))
                  assoc file-name gfy-name))
  (check-file [this file-name]
              (not (nil? (get @(:file (service-context this))
                         file-name))))
  (put-token [this token data]
             (swap! (:token (service-context this))
                    assoc token data)
             (log/debug @(:token (service-context this))))
  (get-token [this token]
             (get @(:token (service-context this))
                  token)))
