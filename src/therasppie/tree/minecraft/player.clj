(ns therasppie.tree.minecraft.player
  (:require
    [therasppie.tree.minecraft.entity :as entity]
    [therasppie.tree.minecraft.world :as world]
    [therasppie.tree.network.conn :as conn]
    [therasppie.tree.network.packet.play-client]
    [therasppie.tree.util.general :refer :all])
  (:import
    (it.unimi.dsi.fastutil.longs LongArrayList)
    (java.util ArrayList)
    (therasppie.tree.minecraft.world Chunk ChunkSection)
    (therasppie.tree.network.packet.play_client ClientChunkData ClientMultiBlockChange ClientUnloadChunk)
    (therasppie.tree.util Arrays Arrays$ODiffConsumer Arrays$CDiffConsumer ChunkMap ChunkMapImpl DiffChunkHelper DiffChunkHelper$IntIntConsumer Vec3i)))

(defn- mk-view []
  {:chunks (ChunkMapImpl.)
   :view-radius nil
   :view-center [0 0]
   :position [0.0 0.0 0.0]
   :rotation [0.0 0.0]
   :entities entity/init})

(defn mk-player [profile conn]
  {::profile profile
   ::conn conn
   ::tick 0
   ::prev-view (mk-view)
   ::next-view (mk-view)
   ::initialised? false
   ::teleport-id nil})

(defn- mk-primary-bit-mask ^long [^Chunk chunk]
  (let [sections (.-sections chunk)]
    (loop [i (dec (count sections))
           bitmask 0]
      (if (>= i 0)
        (let [section (nth sections i)
              bitmask (bit-shift-left bitmask 1)
              bitmask (if-not (identical? section world/empty-chunk-section)
                        (bit-or bitmask 1)
                        bitmask)]
          (recur (dec i) bitmask))
        bitmask))))

(defn- diff-chunks [^long x ^long z ^Chunk chunk1 ^Chunk chunk2]
  (when-not (identical? chunk1 chunk2)
    (let [block-changes (LongArrayList.)
          sections1 (.-sections chunk1)
          sections2 (.-sections chunk2)]
      (Arrays/diffO world/section-count sections1 sections2
        (reify Arrays$ODiffConsumer
          (different [_ section-index section1 section2]
            (Arrays/diffC world/block-count (.-blocks ^ChunkSection section1) (.-blocks ^ChunkSection section2)
              (reify Arrays$CDiffConsumer
                (different [_ i _ new-val]
                  (let [xz (bit-or (bit-shift-left (bit-and i 0xf) 4)
                                   (bit-and (unsigned-bit-shift-right i 4) 0xf))
                        y (bit-or (bit-shift-left section-index 4)
                                  (unsigned-bit-shift-right i 8))
                        block-id (unchecked-int new-val)
                        block-change (bit-or (bit-shift-left xz 40)
                                             (bit-shift-left y 32)
                                             block-id)]
                    (.add block-changes block-change))))))))
      (when-not (.isEmpty block-changes)
        (ClientMultiBlockChange. :multi-block-change x z block-changes)))))

(defn- resend-all-chunks [player]
  (let [{{prev-vr :view-radius
          prev-vc :view-center
          ^ChunkMap prev-chunks :chunks} ::prev-view
         {next-vr :view-radius
          next-vc :view-center
          ^ChunkMap next-chunks :chunks} ::next-view
         conn ::conn} player]
    (when prev-vr
      (let [prev-vr (long prev-vr)
            prev-vc-x (long (prev-vc 0))
            prev-vc-z (long (prev-vc 1))]
        (for-in [x (- prev-vr) (+ prev-vr)]
          (for-in [z (- prev-vr) (+ prev-vr)]
            (conn/send-no-flush conn
              (ClientUnloadChunk. :unload-chunk (+ prev-vc-x x) (+ prev-vc-z z)))))))
    (let [next-vr (long next-vr)
          next-vc-x (long (next-vc 0))
          next-vc-z (long (next-vc 1))]
      (for-in [x (- next-vr) (+ next-vr)]
        (for-in [z (- next-vr) (+ next-vr)]
          (conn/send-no-flush conn
            (let [x (+ next-vc-x x)
                  z (+ next-vc-z z)
                  chunk (.get next-chunks x z world/empty-chunk)]
              (ClientChunkData. :chunk-data x z true (mk-primary-bit-mask chunk) chunk []))))))
    (conn/flush conn)
    (ChunkMapImpl/copy next-chunks prev-chunks)
    (update player ::prev-view conj
      {:view-radius next-vr
       :view-center next-vc})))

(defn- update-chunks [player]
  (let [{{prev-vr :view-radius
          prev-vc :view-center
          ^ChunkMap prev-chunks :chunks} ::prev-view
         {next-vc :view-center
          ^ChunkMap next-chunks :chunks} ::next-view
         conn ::conn} player]
    (DiffChunkHelper/diffChunks
      (prev-vc 0) (prev-vc 1) (next-vc 0) (next-vc 1) prev-vr
      (reify DiffChunkHelper$IntIntConsumer
        (accept [_ x z]
          (.dissoc prev-chunks x z)
          (conn/send-no-flush conn
            (ClientUnloadChunk. :unload-chunk x z))))
      (reify DiffChunkHelper$IntIntConsumer
        (accept [_ x z]
          (let [chunk (.get next-chunks x z world/empty-chunk)]
            (.assoc prev-chunks x z chunk)
            (conn/send-no-flush conn (ClientChunkData. :chunk-data x z true (mk-primary-bit-mask chunk) chunk [])))))
      (if (identical? prev-chunks next-chunks)
        (reify DiffChunkHelper$IntIntConsumer
          (accept [_ _ _]))
        (reify DiffChunkHelper$IntIntConsumer
          (accept [_ x z]
            (let [next-chunk (.get next-chunks x z world/empty-chunk)
                  prev-chunk (or (.assoc prev-chunks x z next-chunk) world/empty-chunk)]
              (when-let [packet (diff-chunks x z prev-chunk next-chunk)]
                (conn/send-no-flush conn packet)))))))
    (conn/flush conn)
    (update player ::prev-view assoc
      :view-center next-vc)))

(defn- keep-alive [player]
  (when (zero? (rem (::tick player) 20))
    (conn/send (::conn player)
      {:packet-type :keep-alive
       :keep-alive-id (::tick player)}))
  player)

(defmacro ->>> [sym & forms]
  (if forms
    `(let [~sym ~(first forms)]
       (->>> ~sym ~@(next forms)))
    sym))

(defn- diff-player [player]
  (let [{{prev-vr :view-radius} ::prev-view
         {next-vr :view-radius :as next-view} ::next-view
         tick ::tick
         conn ::conn} player]
    (if next-vr
      (->>> player
        (if-not (= prev-vr next-vr)
          (resend-all-chunks player)
          (update-chunks player))
        (if-not prev-vr
          (do
            (conn/send conn
              {:packet-type :player-position-and-look
               :position (:position next-view)
               :rotation (:rotation next-view)
               :flags #{}
               :teleport-id tick})
            (assoc player ::teleport-id tick))
          player))
      player)))

(defn- join-game [player]
  (if (::initialised? player)
    player
    (do
      (conn/send (::conn player)
        {:packet-type :join-game
         :entity-id 0
         :gamemode :creative
         :dimension :overworld
         :difficulty :normal
         :max-players 0
         :level-type "default"
         :reduced-debug-info false})
      (assoc player ::initialised? true))))

(defn- world->chunk ^long [^double d]
  (bit-shift-right (long (Math/floor d)) 4))

(defn- set-next-view-center [player]
  (update player ::next-view
    #(assoc % :view-center
       (let [pos (:position %)]
         [(world->chunk (pos 0)) (world->chunk (pos 2))]))))

(defn- diff-entities [player]
  (let [packets (ArrayList.)
        conn (::conn player)]
    (entity/diff-entities packets
      (-> player ::prev-view :entities)
      (-> player ::next-view :entities))
    (run! #(conn/send-no-flush conn %) packets)
    (conn/flush conn))
  (assoc-in player [::prev-view :entities]
    (-> player ::next-view :entities)))

(defn tick-player [player]
  (-> player
      (set-next-view-center)
      (keep-alive)
      (join-game)
      (diff-player)
      (diff-entities)
      (update ::tick inc)))

(defmulti handle-packet (fn [_ packet] (:packet-type packet)))

(defmethod handle-packet :default [player packet]
  (println "Unhandled packet:")
  (prn packet)
  player)

(defmethod handle-packet :keep-alive [player _]
  player)

(defmethod handle-packet :client-settings [player packet]
  (assoc-in player [::next-view :view-radius]
    (-> packet :view-distance (max 2) (min 32))))

(defmethod handle-packet :player-digging [player packet]
  (update-in player [::prev-view :chunks]
    #(let [pos (:position packet)
           pos (Vec3i. (pos 0) (pos 1) (pos 2))]
       (world/raw-block % pos 0))))

(defmethod handle-packet :teleport-confirm [player packet]
  (if (= (::teleport-id player) (:teleport-id packet))
    (assoc player ::teleport-id nil)
    player))

(defmethod handle-packet :player [player _]
  player)

(defmethod handle-packet :player-position [player {position :position}]
  (if-not (::teleport-id player)
    (-> player
        (assoc-in [::prev-view :position] position)
        ((::on-move player) position))
    player))

(defmethod handle-packet :player-look [player {rotation :rotation}]
  (if-not (::teleport-id player)
    (-> player
        (assoc-in [::prev-view :rotation] rotation)
        ((::on-rotate player) rotation))
    player))

(defmethod handle-packet :player-position-and-look [player {position :position rotation :rotation}]
  (if-not (::teleport-id player)
    (-> player
        (assoc-in [::prev-view :position] position)
        (assoc-in [::prev-view :rotation] rotation)
        ((::on-move player) position)
        ((::on-rotate player) rotation))
    player))

(defmethod handle-packet :chat-message [player packet]
  ((::on-chat player) player (:message packet)))
