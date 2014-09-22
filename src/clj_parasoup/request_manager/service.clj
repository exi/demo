(ns clj-parasoup.request-manager.service
  (:require [clojure.tools.logging :as log]
            [clj-parasoup.request-manager.core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.app :refer [get-service]]))


(defprotocol RequestManagerService)

(trapperkeeper/defservice
  request-manager-service
  RequestManagerService
  [[:ConfigService get-in-config]
   [:HttpService set-request-handler]
   [:HttpProxyService proxy-request]
   [:ShutdownService request-shutdown]
   HttpAuthService
   DatabaseService
   [:GfyFetcher fetch]
   [:StaticContentService respond]]
  (start [this context]
         (log/info "Starting requestmanagerservice")
         (let [host (get-in-config [:parasoup :host])
               port (Integer. (get-in-config [:parasoup :port]))]
           (set-request-handler (core/create-request-dispatcher
                                 {:domain (if (= 80 port)
                                            host
                                            (str host ":" port))
                                  :host host
                                  :port port
                                  :db (get-service this :DatabaseService)
                                  :auth (get-service this :HttpAuthService)
                                  :gfy-fetcher fetch
                                  :static-server respond
                                  :proxy-fn proxy-request
                                  :shutdown request-shutdown})))
         context))
