(ns clj-parasoup.request-manager.core
  (:require [co.paralleluniverse.pulsar.core :as pc]
            [clojure.string :as string]
            [org.httpkit.client :as http]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.http-auth.service :as auth]
            [clj-parasoup.util.util :as util]
            [clojure.tools.logging :as log]
            [digest]))

(def gfycat-embed-code "<script>
  var lastGfy = new Date().getTime();
  function removeGfyItems() {
    var items = document.querySelectorAll('.gfyitem');
    for (var i = 0; i < items.length ; i++) {
      items[i].removeAttribute('class');
    }
  }
  function updateGfy() {
    if (new Date().getTime() - lastGfy > 1000) {
      gfyCollection.init();
      lastGfy = new Date().getTime();
      removeGfyItems();
      console.log('update');
    }
  }
  function loadGfy() {
    if (typeof gfyCollection !== 'undefined') return;
    console.log(\"gfy!\");
    (function(d, t){var g = d.createElement(t), s = d.getElementsByTagName(t)[0];
    g.src = 'http://assets.gfycat.com/js/gfyajax-0.517d.js';
    s.parentNode.insertBefore(g, s);}(document, 'script'));
    document.body.addEventListener('DOMSubtreeModified', updateGfy, false);
  }
  </script>")

(def gfycat-run-code "<script type=\"text/javascript\">
  if (loadGfy) {
    loadGfy();
  }
  </script>")

(def max-asset-cache-lifetime-in-seconds (* 60 60 24 365))

(defn asset-request? [request]
  (not (nil? (re-find #"^asset-" (get-in request [:headers "host"])))))

(defn create-etag [request]
  (digest/md5 (:uri request)))

(defn log-access [request hit]
  (log/info
   (if hit "hit" "miss")
   (str (get-in request [:headers "host"]) (:uri request))))


(def cache-control-header (str "public, max-age=" max-asset-cache-lifetime-in-seconds))

(defn responde-from-cache [opts file-data]
  (log-access (:request opts) true)
  {:status 200
   :body (:byte-data file-data)
   :headers {"content-type" (:content-type file-data)
             "cache-control" cache-control-header
             "etag" (create-etag (:request opts))}})

(defn add-gfycat-to-head [body]
  (-> body
      (string/replace #"</head>" (str gfycat-embed-code "</head>") )
      (string/replace #"</body>" (str gfycat-run-code "</body>"))))

(defn fetch-gfys [gifs opts]
   (into
    []
    (->>
     (map
      (fn [gif]
        (let [fetched {:gif gif :gfy (dbp/get-gfy (:db opts) (util/extract-gif-uri-from-url gif))}]
          (when (nil? (:gfy fetched))
            ((:gfy-fetcher opts) gif))
          fetched))
      gifs)
     (remove #(nil? (:gfy %1))))))

(defn replace-found-gfys [body gfy-list]
  (reduce
   (fn [body gfy]
     (let [new  (string/replace
                 body
                 (str "src=\"" (:gif gfy) "\"")
                 (str "class=\"gfyitem\" data-id=\"" (:gfy gfy) "\""))]
       new))
   body
   gfy-list))

(defn gif->gfycat [response opts]
  (log/debug "gfycat" (get-in opts [:request :uri]))
  (if (or (nil? (get-in response [:headers "content-type"]))
          (not (string? (:body response)))
          (not (re-matches #".*text/html.*" (get-in response [:headers "content-type"])))
          (not (= 200 (:status response))))
    response
    (let [body (:body response)
          gfys (fetch-gfys (re-seq #"http://asset-[^\"]+\.gif" body) opts)]
      (assoc
        response
        :body
        (-> body
            (add-gfycat-to-head)
            (replace-found-gfys gfys))))))

(defn apply-etag [response request]
  (log/debug "etag")
  (if (= 200 (:status response))
    (assoc-in response [:headers "etag"] (create-etag request))
    response))

(pc/defsfn responde-from-soup [opts]
  (log/debug "from soup" (get-in opts [:request :uri]))
  (let [request (:request opts)
        response ((:proxy-fn opts)
                  request
                  (:domain opts))]
    (when (and (= 200 (:status response))
               (asset-request? request))
      (log-access request false)
      (dbp/put-file (:db opts)
                    (:uri request)
                    (:body response)
                    (get-in response [:headers "content-type"])))
    (assoc-in (gif->gfycat response opts)
              [:headers "etag"]
              (create-etag (:request opts)))))

(defn responde-with-304 [opts]
  {:status 304
   :body nil
   :headers {"etag" (create-etag (:request opts))
             "cache-control" cache-control-header}})

(defn responde-with-static [opts]
  (log/debug "serve static")
  ((:static-server opts) (:request opts)))

(defn serve-static? [opts]
  (re-find (re-pattern (str "^" (:host opts)))
           (get-in opts [:request :headers "host"])))

(pc/defsfn responde-non-asset
  [opts]
  (if (serve-static? opts)
    (responde-with-static opts)
    (responde-from-soup opts)))

(pc/defsfn handle-authenticated-request
  [opts]
  (let [request (:request opts)
        etag (get-in request [:headers "if-none-match"])]
    (if (and (asset-request? request)
             etag
             (dbp/check-file (:db opts) (:uri request)))
      (responde-with-304 opts)
      (if-let [file-data (when (asset-request? request)
                           (dbp/get-file (:db opts) (:uri request)))]
        (responde-from-cache opts file-data)
        (responde-non-asset opts)))))

(pc/defsfn request-dispatcher
  [opts]
  (let [request (:request opts)
        auth-service (:auth opts)]
    (when (= "/shutdown" (:uri request)) ((:shutdown opts)))
    (if (asset-request? request)
      (handle-authenticated-request opts)
      (auth/handle-request auth-service opts handle-authenticated-request))))

(pc/defsfn create-request-dispatcher [opts]
  (fn [request]
    (request-dispatcher (assoc opts
                          :request request))))
