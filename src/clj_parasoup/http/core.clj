(ns clj-parasoup.http.core
  (:require [clj-parasoup.util.async :as uas]
            [co.paralleluniverse.pulsar.core :as pc]
            [aleph.http :as ahttp]
            [aleph.formats :as af]
            [lamina.core :as lc]
            [clojure.tools.logging :as log]))

(pc/defsfn receive-complete-lamina-channel [channel]
  (let [ret (pc/promise)
        agg (lc/reduce* conj [] channel)]
    (log/debug "receive complete channel")
    (lc/on-closed
     channel
     (fn []
       (deliver ret (af/channel-buffers->channel-buffer @agg))))
    @ret))

(pc/defsfn unwrap-async-body [body]
  (let [result (if (and body (lc/channel? body))
                 (receive-complete-lamina-channel body)
                 body)]
    result))

(defn async-body? [data]
  (let [body (:body data)]
    (lc/channel? body)))

(defn handle-request [handler aleph-channel request]
  (pc/spawn-fiber
   #(lc/enqueue
     aleph-channel
     (@handler (if (async-body? request)
                 (assoc request :body (unwrap-async-body (:body request)))
                 request)))))

(defn aleph-handler [handler]
  (fn [aleph-channel request] (handle-request handler aleph-channel request)))

(defn default-handler []
  (atom (fn [request]
          {:status 404
           :headers {"content-type" "text/plain"}
           :body "Not Found"})))

(defn start-server [config handler]
  (ahttp/start-http-server (aleph-handler handler) config))
