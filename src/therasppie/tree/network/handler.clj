(ns therasppie.tree.network.handler
  (:require
    [better-cond.core :as b]
    [therasppie.tree.minecraft.server :as server]
    [therasppie.tree.network.conn :as conn]
    [therasppie.tree.network.encryption :as encryption]
    [therasppie.tree.network.protocol :as protocol]
    [therasppie.tree.network.session :as session])
  (:import
    (io.netty.channel Channel ChannelFutureListener)
    (java.nio.charset StandardCharsets)
    (java.security GeneralSecurityException)
    (java.util Arrays Queue UUID)
    (javax.crypto.spec IvParameterSpec SecretKeySpec)
    (javax.crypto Cipher)
    (therasppie.tree.network.codec CipherCodec)))

(declare handshaking-state status-state status-ping-state login-state encryption-state has-joined-waiting-state play-state closed-state do-login)

(defn mk-handler [server conn]
  (let [state (volatile! {:tag :handshaking})]
    (letfn [(dispatch [packet]
              (let [f (case (:tag @state)
                        :handshaking handshaking-state
                        :status status-state
                        :status-ping status-ping-state
                        :login login-state
                        :encryption encryption-state
                        :has-joined-waiting has-joined-waiting-state
                        :play play-state
                        :closed closed-state)]
                (vswap! state f packet conn server dispatch)))]
      (conn/add-listener (.closeFuture ^Channel conn)
        (reify ChannelFutureListener
          (operationComplete [_ _]
            (dispatch
              {:packet-type :connection-closed}))))
      dispatch)))

(defn- handshaking-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :handshake
    (case (int (:next-state packet))
      1
      (do
        (conn/set-serialize-fn! conn (protocol/mk-serialize-fn protocol/status))
        {:tag :status})

      2
      (do
        (conn/set-serialize-fn! conn (protocol/mk-serialize-fn protocol/login))
        {:tag :login})

      (do
        (conn/close-immediately conn)
        {:tag :closed}))

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- status-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :request
    (do
      (conn/send conn
        {:packet-type :response
         :response {:version {:name "1.12.2"
                              :protocol 340}
                    :players {:max 100
                              :online (-> @server :players count)}
                    :description "Tree v0.0.1-SNAPSHOT"}})
      {:tag :status-ping})

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- status-ping-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :ping
    (do
      (conn/send-and-close conn
        {:packet-type :pong
         :payload (:payload packet)})
      {:tag :closed})

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- start-thread [f]
  (.start (Thread. ^Runnable f)))

(defn- submit [^Channel channel f]
  (.submit (.eventLoop channel) ^Runnable f))

(defn- login-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :login-start
    (b/cond
      :let [username (:username packet)]
      (not (re-matches #"^[A-Za-z0-9_]{3,16}$" username))
      (do
        (conn/send-and-close conn
          {:packet-type "disconnect"
           :reason (str "Invalid username: " username)})
        {:tag :closed})

      (not ((-> @server :adapters :online-mode?) username))
      (do-login server conn
        (session/map->GameProfile
          {:unique-id (UUID/nameUUIDFromBytes (.getBytes (str "Offline-Player:" username) StandardCharsets/UTF_8))
           :username username
           :properties []}))

      :let [key-pair (:key-pair @server)
            request (encryption/mk-request key-pair)]
      (do
        (conn/send conn (assoc request :packet-type :encryption-request :server-id ""))
        {:tag :encryption
         :username username
         :key-pair key-pair
         :request request}))

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- encryption-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :encryption-response
    (b/cond
      :let [response (try
                       (encryption/decrypt-response (:key-pair state) packet)
                       (catch GeneralSecurityException _))]
      (not response)
      (do
        (conn/close-immediately conn)
        {:tag :closed})

      :let [request (:request state)
            shared-secret (:shared-secret response)]
      (not (and (Arrays/equals ^bytes (:verify-token response) ^bytes (:verify-token request))
                (= 16 (count shared-secret))))
      (do
        (conn/close-immediately conn)
        {:tag :closed})

      :let [secret-key (SecretKeySpec. shared-secret "AES")
            mk-cipher (fn [^long mode]
                        (doto (Cipher/getInstance "AES/CFB8/NoPadding")
                          (.init mode secret-key (IvParameterSpec. (.getEncoded secret-key)))))
            hash (encryption/hash-byte-arrays [shared-secret (:public-key request)])
            [encrypt decrypt] (try
                                [(mk-cipher Cipher/ENCRYPT_MODE) (mk-cipher Cipher/DECRYPT_MODE)]
                                (catch GeneralSecurityException _))]
      (not (and encrypt decrypt))
      (do
        (conn/close-immediately conn)
        {:tag :closed})

      (do
        (conn/add-before conn "length-codec" "cipher-codec" (CipherCodec. encrypt decrypt))
        (start-thread
          (fn []
            (let [profile (try (session/has-joined (:username state) hash) (catch Throwable _))]
              (submit conn
                #(dispatch
                   {:packet-type :has-joined
                    :profile profile})))))
        (assoc state :tag :has-joined-waiting)))

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- has-joined-waiting-state [state packet conn server dispatch]
  (case (:packet-type packet)
    :has-joined
    (let [profile (:profile packet)]
      (if (and profile (= (:username state) (:username profile)))
        (do-login server conn profile)
        (do
          (conn/send-and-close conn
            {:packet-type :disconnect
             :reason "Could not authenticate with Mojang's servers"})
          {:tag :closed})))

    (do
      (conn/close-immediately conn)
      {:tag :closed})))

(defn- do-login [server conn profile]
  (conn/send conn
    {:packet-type :login-success
     :uuid (str (:unique-id profile))
     :username (:username profile)})
  (conn/set-serialize-fn! conn (protocol/mk-serialize-fn protocol/play))
  (let [connection (server/->Connection (volatile! nil) profile conn)
        queue ^Queue (:message-queue @server)]
    (.offer queue (server/->ConnectMsg connection))
    {:tag :play
     :connection connection
     :queue queue}))

(defn- play-state [state packet conn server dispatch]
  (let [connection (:connection state)
        queue ^Queue (:queue state)]
    (case (:packet-type packet)
      :connection-closed
      (do
        (.offer queue (server/->DisconnectMsg connection))
        {:tag :closed})

      (do
        (.offer queue (server/->ReceivePacketMsg connection packet))
        state))))

(defn- closed-state [state packet conn server dispatch]
  state)


#_(defn mk-handshaking-handler [server conn]
    (fn [packet]
      (case (:packet-type packet)
        :handshake
        (condp = (:next-state packet)
          1
          (do (conn/set-serialize-fn! conn (protocol/mk-serialize-fn protocol/status))
              (conn/set-handle-fn! conn (mk-status-handler server conn)))
          2
          (do (conn/set-serialize-fn! conn (protocol/mk-serialize-fn protocol/login))
              (conn/set-handle-fn! conn (mk-login-handler server conn)))
          (conn/close-immediately conn)))))

#_(defn mk-status-handler [server conn]
    (let [state (atom :request)]
      (fn [packet]
        (case (:packet-type packet)
          :request
          (if (compare-and-set! state :request :ping)
            (conn/send conn
              {:packet-type :response
               :response {:version {:name "1.12.1"
                                    :protocol 338}
                          :players {:max 100
                                    :online (-> @server :players count)}
                          :description ""}})
            (conn/close-immediately conn))
          :ping
          (if (compare-and-set! state :ping :end)
            (conn/send-and-close conn
              {:packet-type :pong
               :payload (:payload packet)})
            (conn/close-immediately conn))))))

#_(defn mk-login-handler [server conn]
    (fn [packet]
      (case (:packet-type packet)
        :login-start
        (b/cond
          :let [username (:username packet)]
          (not (re-matches #"^[A-Za-z0-9_]{3,16}$" username))
          (conn/send-and-close conn
            {:packet-type "disconnect"
             :reason (str "Invalid username: " username)})
          (not ((-> @server :adapters :online-mode?) username))
          (do-login server conn
            (session/map->GameProfile
              {:unique-id (UUID/nameUUIDFromBytes (.getBytes (str "Offline-Player:" username) StandardCharsets/UTF_8))
               :username username
               :properties []}))
          (let [key-pair (:key-pair @server)
                request (encryption/mk-request key-pair)]
            (conn/send conn (assoc request :packet-type :encryption-request :server-id ""))
            (conn/set-handle-fn! conn
              (fn [packet]
                (case (:packet-type packet)
                  :login-start
                  (conn/close-immediately conn)
                  :encryption-response
                  (b/cond
                    :let [response (try
                                     (encryption/decrypt-response key-pair packet)
                                     (catch GeneralSecurityException _))]
                    (not response)
                    (conn/close-immediately conn)
                    :let [shared-secret (:shared-secret response)]
                    (not (and (Arrays/equals ^bytes (:verify-token response) ^bytes (:verify-token request))
                              (= 16 (count shared-secret))))
                    (conn/close-immediately conn)
                    :let [secret-key (SecretKeySpec. shared-secret "AES")
                          mk-cipher (fn [^long mode]
                                      (doto (Cipher/getInstance "AES/CFB8/NoPadding")
                                        (.init mode secret-key (IvParameterSpec. (.getEncoded secret-key)))))
                          hash (encryption/hash-byte-arrays [shared-secret (:public-key request)])
                          [encrypt decrypt] (try
                                              [(mk-cipher Cipher/ENCRYPT_MODE) (mk-cipher Cipher/DECRYPT_MODE)]
                                              (catch GeneralSecurityException _))]
                    (not (and encrypt decrypt))
                    (conn/close-immediately conn)
                    (do
                      (conn/add-before conn "length-codec" "cipher-encoder" (CipherCodec/encoder (mk-cipher Cipher/ENCRYPT_MODE)))
                      (conn/add-before conn "length-codec" "cipher-decoder" (CipherCodec/decoder (mk-cipher Cipher/DECRYPT_MODE)))
                      (start-thread
                        (fn []
                          (let [profile (try (session/has-joined username hash) (catch Throwable _))]
                            (submit conn
                              (fn []
                                (when (conn/connected? conn)
                                  (if (not (and profile (= username (:username profile))))
                                    (conn/send-and-close conn
                                      {:packet-type :disconnect
                                       :reason "Could not authenticate with Mojang's servers"})
                                    (do-login server conn profile)))))))))))))))
        :encryption-response
        (conn/close-immediately conn))))
