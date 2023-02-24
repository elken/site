;; Copyright © 2021, JUXT LTD.

(ns juxt.site.util
  (:require
   [juxt.clojars-mirrors.nippy.v3v1v1.taoensso.nippy.utils :refer [freezable?]])
  (:import
   (com.auth0.jwt JWT)
   (com.auth0.jwt.algorithms Algorithm)))

(defn assoc-when-some [m k v]
  (cond-> m v (assoc k v)))

;;TODO find out what is different about this compared to assoc-when-some above
(defn assoc-some
  "Associates a key with a value in a map, if and only if the value is not nil."
  ([m k v]
   (if (or (nil? v) (false? v)) m (assoc m k v)))
  ([m k v & kvs]
   (reduce (fn [m [k v]] (assoc-some m k v))
           (assoc-some m k v)
           (partition 2 kvs))))

(defn hexdigest
  "Returns the hex digest of an object.
  computing entity-tags."
  ([input] (hexdigest input "SHA-256"))
  ([input hash-algo]
   (let [hash (java.security.MessageDigest/getInstance hash-algo)]
     (. hash update input)
     (let [digest (.digest hash)]
       (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))))

(defn uuid-str []
  (str (java.util.UUID/randomUUID)))

(defn uuid-bytes []
  (.getBytes (uuid-str)))

(defn sha
  "Return a byte-array"
  ([bytes] (sha bytes "SHA3-224"))
  ([bytes algo]
   (let [hash (java.security.MessageDigest/getInstance algo)]
     (. hash update bytes)
     (.digest hash))))

(defn random-bytes [size]
  (let [result (byte-array size)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) result)
    result))

(defn as-hex-str
  "This uses java.util.HexFormat which requires Java 17 and above. If required,
  this can be re-coded, see
  https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
  and similar. For the size parameter, try 12."
  [bytes]
  (.formatHex (java.util.HexFormat/of) bytes))

(defn as-b64-str [bytes]
  (.encodeToString (java.util.Base64/getEncoder) bytes))

(def mime-types
  {"html" "text/html;charset=utf-8"
   "js" "application/javascript"
   "map" "application/json"
   "css" "text/css"
   "png" "image/png"
   "adoc" "text/asciidoc"})

(defn paths
  "Given a nested structure, return the paths to each leaf."
  [form]
  (if (coll? form)
    (for [[k v] (if (map? form) form (map vector (range) form))
          w (paths v)]
      (cons k (if (coll? w) w [w])))
    (list form)))

(comment
  (for [path
        (paths {:x {:y {:z [:a :b :c] :z2 [0 1 {:u {:v 1}}]}}
                :p {:q {:r :s :t :u :y (fn [_] nil)}}})
        :when (not (fn? (last path)))]
    path))

(defn deep-replace
  "Apply f to x, where x is a map value, collection member or scalar, anywhere in
  the form's structure. This is similar, but not identical to,
  clojure.walk/postwalk."
  [form f]
  (cond
    (map? form) (reduce-kv (fn [acc k v] (assoc acc k (deep-replace v f))) {} form)
    (vector? form) (mapv (fn [i] (deep-replace i f)) form)
    (coll? form) (map (fn [i] (deep-replace i f)) form)
    :else (f form)))

(comment
  (deep-replace {:a :b :c [identity {:x [{:g [:a :b identity]}]}]} #(if (fn? %) :replaced %)))

(defn ->freezeable [form]
  (deep-replace
   form
   (fn [form]
     (cond-> form
       (not (freezable? form))
       ((fn [_] :juxt.site/unfreezable))))))

(defn etag [representation]
  (format
   "\"%s\""
   (subs
    (hexdigest
     (cond
       (:juxt.http/body representation)
       (:juxt.http/body representation)
       (:juxt.http/content representation)
       (.getBytes (:juxt.http/content representation)
                  (get representation :juxt.http/charset "UTF-8")))) 0 32)))

(defn make-nonce
  "This uses java.util.HexFormat which requires Java 17 and above. If required,
  this can be re-coded, see
  https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
  and similar. For the size parameter, try 12."
  [size]
  (as-hex-str (random-bytes size)))

;; TODO: Test me
(defn make-jwt
  [claims]
  (->
   (reduce-kv
    (fn [acc n claim]
      (if (nil? claim)
        (.withNullClaim acc n)
        (case n
          "aud" (.withAudience acc (into-array String [claim]))
          "exp" (.withExpiresAt acc claim)
          "iat" (.withIssuedAt acc claim)
          "iss" (.withIssuer acc claim)
          "jti" (.withJWTId acc claim)
          "kid" (.withKeyId acc claim)
          "ntb" (.withNotBefore acc claim)
          "sub" (.withSubject acc claim)
          (if (sequential? claim)
            (let [fc (first claim)]
              (.withArrayClaim acc n (cond
                                       (instance? Integer fc) (into-array Integer claim)
                                       (instance? Long fc) (into-array Long claim)
                                       :else (into-array String (map str claim)))))
            (.withClaim acc n claim)))))
    (JWT/create)
    claims)
   (.sign (Algorithm/none))))

(defn decode-access-token [jwt]
  (let [{:strs [exp iat nbf aud iss jti]} (-> jwt JWT/decode (.getClaims))]
    (cond-> {}
      exp (assoc "exp" (.asLong exp))
      iat (assoc "iat" (.asLong iat))
      nbf (assoc "nbf" (.asLong nbf))
      aud (assoc "aud" (.asString aud))
      iss (assoc "iss" (.asString iss))
      jti (assoc "jti" (.asString jti)))))
