(ns clj-parasoup.http-auth.core
  (:require [co.paralleluniverse.pulsar.core :as pc]
            [clj-parasoup.database.protocol :as dbp]
            [clj-parasoup.util.header-parser :as hp]
            [clj-parasoup.util.random-strings :as rs]
            [clojure.data.codec.base64 :as b64]
            [clojure.tools.logging :as log]
            [clojure.string :as string]))

(def cookie-name "parasoup-token")
(def cookie-max-age (* 60 60 24 365))
(def token-length 32)

(defn valid-token? [db token]
  (not (nil? (dbp/get-token db token))))

(defn create-auth-text [username password]
  (String. (b64/encode (.getBytes (str username ":" password)))))

(defn authenticated? [db username password request]
  (let [headers (:headers request)
        parsed-cookie (hp/parse-cookies-from-headers headers)]
    (if-let [token (get parsed-cookie cookie-name)]
      (valid-token? db token)
      false)))

(defn successfull-auth-attempt? [username password request]
  (let [headers (:headers request)
        auth-response (hp/parse-auth-response-from-headers headers)
        target-auth-text (create-auth-text username password)]
    (= auth-response target-auth-text)))

(defn send-auth-request []
  {:status 401
   :body "Authentication required"
   :headers {"WWW-Authenticate" "Basic realm=\"Please log in\""}})

(defn set-token-cookie-in-response [domain response token]
  (assoc-in
    response
    [:headers "set-cookie"]
    (str cookie-name "=" token ";path=/;domain=." domain ";max-age=" cookie-max-age)))

(pc/defsfn wrap-successfull-auth-attempt [db username domain next-fn]
  (let [token (rs/get-hex-string token-length)]
    (log/info "wrap auth attempt")
    (dbp/put-token db token {})
    (let [response (next-fn)
          new-response (set-token-cookie-in-response domain response token)]
      new-response)))

(pc/defsfn handle-auth-request [db username password domain opts next-fn]
  (if (authenticated? db username password (:request opts))
    (next-fn opts)
    (if (successfull-auth-attempt? username password (:request opts))
      (wrap-successfull-auth-attempt db username domain (partial next-fn opts))
      (send-auth-request))))
