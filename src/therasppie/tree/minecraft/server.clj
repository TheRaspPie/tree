(ns therasppie.tree.minecraft.server
  (:require
    [therasppie.tree.minecraft.player :as player]
    [therasppie.tree.minecraft.world :as world]
    [therasppie.tree.network.conn :as conn]
    [therasppie.tree.network.encryption :as encryption]
    [therasppie.tree.util.exception :as ex]
    [therasppie.tree.util.path :as path]
    [therasppie.tree.util.time :as time])
  (:import
    (java.io PrintStream)
    (java.util Queue)
    (java.util.concurrent ConcurrentLinkedQueue)
    (org.HdrHistogram HistogramLogWriter SingleWriterRecorder)
    (therasppie.tree.util ChunkMapImpl)))

(defn load-chunks [name]
  (world/read-all-chunks-from-region-directory (ChunkMapImpl.) (path/resolve (path/get name) "region")))

(defrecord Connection [pl profile conn])
(defrecord ConnectMsg [connection])
(defrecord DisconnectMsg [connection])
(defrecord ReceivePacketMsg [connection packet])
(defrecord RunTaskMsg [f])

(defn- process-msg [msg server]
  (condp instance? msg
    ReceivePacketMsg
    (let [pl (-> msg :connection :pl)]
      (when @pl
        (vswap! pl player/handle-packet (:packet msg)))
      server)
    ConnectMsg
    (let [{:keys [pl profile conn]} (:connection msg)
          player (player/mk-player profile conn)]
      (if-let [player ((-> server :adapters :join) player)]
        (do
          (println "Player connected:")
          (prn profile)
          (vreset! pl player)
          (update server :players conj pl))
        server))
    DisconnectMsg
    (let [pl (-> msg :connection :pl)]
      (if ((:players server) pl)
        (do
          (println "Player disconnected:")
          (prn (-> msg :connection :profile))
          (update server :players disj pl))
        server))
    RunTaskMsg
    ((:f msg) server)))

(defn schedule [server f]
  (.offer ^Queue (:message-queue server) (->RunTaskMsg f)))

(defn- mk-server [adapters]
  {:message-queue (ConcurrentLinkedQueue.)
   :key-pair (encryption/mk-key-pair)
   :adapters adapters
   :players #{}
   :tick 0})

(defn- tick-server [server]
  (let [queue ^Queue (:message-queue server)
        server (loop [server server]
                 (if-let [msg (.poll queue)]
                   (recur (process-msg msg server))
                   server))]
    (run! #(vswap! % player/tick-player) (:players server))
    (update server :tick inc)))

(defn- sleep [^long millis]
  (try
    (Thread/sleep millis)
    (catch InterruptedException _)))

(defn- advance-server [server ^SingleWriterRecorder recorder]
  (let [sample-delay-ns (time/convert time/seconds time/nanos 10)
        tick-time-ns (time/convert time/millis time/nanos 10)
        time (System/nanoTime)]
    (vswap! server tick-server)
    (let [elapsed-ns (- (System/nanoTime) time)]
      (when (> (* (:tick @server) tick-time-ns) sample-delay-ns))
      (.recordValue recorder elapsed-ns)
      (if (< elapsed-ns tick-time-ns)
        (sleep (time/convert time/nanos time/millis (- tick-time-ns elapsed-ns)))
        (println "Server running behind: Took" (time/convert time/nanos time/micros elapsed-ns) "microseconds")))))

(defn start-server [{:keys [hostname port adapters mk-serialize-fn mk-handle-fn]}]
  (let [start-time (System/currentTimeMillis)
        server (volatile! (mk-server adapters))
        shutting-down? (atom false)
        recorder (SingleWriterRecorder. 1000 60000000000 3)
        reporting-thread
        (-> (fn []
              (with-open [stream (PrintStream. "histogram.hlog")]
                (let [writer (HistogramLogWriter. stream)]
                  (.outputStartTime writer start-time)
                  (.setBaseTime writer start-time)
                  (.outputLegend writer)
                  (loop [histogram nil]
                    (when (not @shutting-down?)
                      (let [histogram (.getIntervalHistogram recorder histogram)]
                        (when (pos? (.getTotalCount histogram))
                          (.outputIntervalHistogram writer histogram))
                        (sleep 1000)
                        (recur histogram)))))
                #_(.outputPercentileDistribution histogram stream 1000.0)))
            (Thread. "reporting-thread"))
        server-thread
        (-> (fn []
              (let [listener (conn/listen-on hostname port
                               (conn/mk-init-channel
                                 #(mk-serialize-fn server %)
                                 #(mk-handle-fn server %)))]
                (println "Server starting")
                ((:start listener))
                (println "Server started")
                (try
                  (while (not @shutting-down?)
                    (advance-server server recorder))
                  (catch Throwable t
                    (println "Failed to tick server")
                    (.printStackTrace t)))
                ((:shutdown listener))
                ((:await listener))
                (println "Server stopped")))
            (Thread. "server-thread"))]
    (.start reporting-thread)
    (.start server-thread)
    {:shutdown #(do
                  (when-not (compare-and-set! shutting-down? false true)
                    (ex/throw-state "Server already shutting down"))
                  (.interrupt server-thread)
                  (.interrupt reporting-thread))
     :await #(do
               (.join server-thread)
               (.join reporting-thread))}))
