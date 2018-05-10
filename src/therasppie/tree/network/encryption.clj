(ns therasppie.tree.network.encryption
  (:import
    (java.security KeyPairGenerator MessageDigest)
    (java.util.concurrent ThreadLocalRandom)
    (javax.crypto Cipher)))

(defn mk-key-pair []
  (let [gen (KeyPairGenerator/getInstance "RSA")]
    (.initialize gen 1024)
    (.generateKeyPair gen)))

(defn hash-byte-arrays [byte-arrays]
  (let [digest (MessageDigest/getInstance "SHA-1")]
    (run! #(.update digest %) byte-arrays)
    (.toString (BigInteger. (.digest digest)) 16)))

(defn mk-request [key-pair]
  (let [public-key (.getEncoded (.getPublic key-pair))
        verify-token (byte-array 4)]
    (.nextBytes (ThreadLocalRandom/current) verify-token)
    {:public-key public-key
     :verify-token verify-token}))

(defn decrypt-response [key-pair response]
  (let [cipher (Cipher/getInstance "RSA")
        private-key (.getPrivate key-pair)
        decrypt (fn [encrypted]
                  (.init cipher Cipher/DECRYPT_MODE private-key)
                  (.doFinal cipher encrypted))
        shared-secret (decrypt (:shared-secret response))
        verify-token (decrypt (:verify-token response))]
    {:shared-secret shared-secret
     :verify-token verify-token}))
