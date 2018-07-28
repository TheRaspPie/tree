(ns therasppie.tree.network.packet.play-client
  (:require
    [therasppie.tree.network.packet.enums :as enums]
    [therasppie.tree.network.packet.packet :refer :all])
  (:import
    (io.netty.buffer ByteBuf)
    (it.unimi.dsi.fastutil.longs LongList)
    (java.util.function LongConsumer)
    (therasppie.tree.minecraft.world Chunk ChunkSection)))

(defn- reversed-two-tuple [s1 s2]
  (fn
    ([buf]
     (let [v2 (s2 buf)
           v1 (s1 buf)]
       [v1 v2]))
    ([buf value]
     (when-not (= 2 (count value))
       (throw (Exception. (str "Invalid tuple: " value))))
     (s2 buf (nth value 1))
     (s1 buf (nth value 0)))))

(gen-client
  "spawn-object"
  :entity-id varint
  :unique-id uuid
  :type uint8
  :position float64-xyz
  :rotation (reversed-two-tuple uint8 uint8)
  :data int32
  :velocity int16-xyz)

(gen-client
  "spawn-experience-orb"
  :entity-id varint
  :position float64-xyz
  :count int16)

(gen-client
  "spawn-global-entity"
  :entity-id varint
  :type (enum uint8 {:thunderbolt 1})
  :position float64-xyz)

(def entity-metadata
  (sequence-with-end uint8 0xff
    (composite
      :index uint8
      :type int8
      :value [:dispatch-on [:type]
              #(case (int %)
                 0 int8
                 1 varint
                 2 float32
                 3 (string 255)
                 4 chat
                 5 slot
                 6 bool
                 7 float32-xyz
                 8 position
                 9 (optional position)
                 10 (enum varint {:down 0
                                  :up 1
                                  :north 2
                                  :south 3
                                  :west 4
                                  :east 5})
                 11 (optional uuid)
                 12 varint
                 13 nbt)])))

(gen-client
  "spawn-mob"
  :entity-id varint
  :unique-id uuid
  :type varint
  :position float64-xyz
  :rotation (tuple uint8 uint8)
  :head-pitch uint8
  :velocity int16-xyz
  :metadata entity-metadata)

(gen-client
  "spawn-painting"
  :entity-id varint
  :entity-uuid uuid
  :title (string 13)
  :position position
  :direction (enum uint8 {:south 0
                          :west 1
                          :north 2
                          :east 3}))

(gen-client
  "spawn-player"
  :entity-id varint
  :player-uuid uuid
  :position float64-xyz
  :rotation (tuple uint8 uint8)
  :metadata entity-metadata)

(gen-client
  "animation"
  :entity-id varint
  :animation (enum uint8 {:swing-main-arm 0
                          :take-damage 1
                          :leave-bed 2
                          :swing-offhand 3
                          :critical-effect 4
                          :magic-critical-effect 5}))

#_(gen-client
    "statistics")

(gen-client
  "block-break-animation"
  :entity-id varint
  :position position
  :destroy-stage uint8)

#_(gen-client
    "update-block-entity"
    :position position
    :action uint8
    :nbt-data nbt)

#_(gen-client
    "block-action"
    :position position
    :action-id uint8
    :action-param uint8
    :block-type varint)

(gen-client
  "block-change"
  :position position
  :block-id varint)

#_(gen-client
    "boss-bar")

(gen-client
  "server-difficulty"
  :difficulty (enum uint8 enums/difficulty))

(gen-client
  "tab-complete"
  :matches (sequence-with-length varint (string 32767)))

(gen-client
  "chat-message"
  :message chat
  :position (enum uint8 {:chat 0
                         :system-message 1
                         :above-hotbar 2}))

(defrecord ClientMultiBlockChange [packet-type ^int x ^int z records])

(defn client-multi-block-change
  ([buf ^ClientMultiBlockChange value]
   (int32 buf (.-x value))
   (int32 buf (.-z value))
   (let [records ^LongList (.-records value)]
     (varint buf (.size records))
     (.forEach records
       (reify LongConsumer
         (accept [_ record]
           (uint8 buf (bit-and (unsigned-bit-shift-right record 40) 0xff)) ;; xz
           (uint8 buf (bit-and (unsigned-bit-shift-right record 32) 0xff)) ;; y
           (varint buf (bit-and record 0xffffffff)))))))) ;; block-id

(gen-client
  "confirm-transaction"
  :window-id uint8
  :action-number int16
  :accepted bool)

(gen-client
  "close-window"
  :window-id uint8)

(gen-client
  "open-window"
  :window-id uint8
  :window-type (string 32)
  :window-title chat
  :number-of-slots uint8
  :entity-id [:dispatch-on [:window-type]
              #(if (= "EntityHorse" %) int32 no-op)])

(gen-client
  "window-items"
  :window-id uint8
  :slot-data (sequence-with-length int16 slot))

#_(gen-client
    "window-property"
    :window-id uint8
    :property int16
    :value int16)

(gen-client
  "set-slot"
  :window-id uint8
  :slot int16
  :slot-data slot)

(gen-client
  "set-cooldown"
  :item-id varint
  :cooldown-ticks varint)

#_(gen-client
    "named-sound-effect"
    :sound-name (string 256)
    :sound-category varint
    :effect-position float32-xyz
    :volume float32
    :pitch float32)

#_(gen-client
    "entity-status")

(gen-client
  "explosion"
  :position float32-xyz
  :radius float32
  :records (sequence-with-length int32
             (tuple uint8 uint8 uint8)) ; TODO: Signed bytes!
  :player-motion float32-xyz)

(defrecord ClientUnloadChunk [packet-type ^int x ^int z])

(defn client-unload-chunk
  ([buf ^ClientUnloadChunk value]
   (int32 buf (.-x value))
   (int32 buf (.-z value))))

(gen-client
  "change-game-state"
  :reason uint8
  :value float32)

(defrecord ClientChunkData
  [packet-type ^int x ^int z
   ^boolean ground-up-continuous? ^int primary-bit-mask
   chunk block-entities])

(defn- write-chunk-data [^ByteBuf buf ^long bit-mask ^Chunk chunk]
  (let [length (+ 256 (* (Long/bitCount bit-mask) (+ 4100 (* 13 64 8))))]
    (varint buf length)
    (.ensureWritable buf length))
  (let [sections ^objects (.-sections chunk)]
    (dotimes [i (alength sections)]
      (when-not (zero? (bit-and bit-mask (bit-shift-left 1 i)))
        (let [section ^ChunkSection (aget sections i)]
          ;; bits per block
          (uint8 buf 13)
          ;; palette length
          (varint buf 0)
          (varint buf (* 13 64))
          (let [blocks ^chars (.-blocks section)
                length (alength blocks)]
            (loop [value 0 bits 0 index (int 0)]
              (when (< index length)
                (let [block (int (aget blocks index))
                      b (bit-and (Integer/toUnsignedLong block) 2r1111111111111)
                      value (bit-or value (bit-shift-left b bits))
                      bits (+ bits 13)]
                  (if (>= bits 64)
                    (do
                      (int64 buf value)
                      (let [bits (- bits 64)
                            value (unsigned-bit-shift-right b (- 13 bits))]
                        (recur value bits (inc index))))
                    (recur value bits (inc index)))))))
          (.writeBytes buf ^bytes (.-blockLight section))
          (.writeBytes buf ^bytes (.-skyLight section))))))
  (.writeBytes buf ^bytes (.-biomes chunk)))

(let [varint-nbt-seq (sequence-with-length varint nbt)]
  (defn client-chunk-data
    ([buf ^ClientChunkData value]
     (int32 buf (.-x value))
     (int32 buf (.-z value))
     (bool buf (.-ground_up_continuous_QMARK_ value))
     (varint buf (.-primary_bit_mask value))
     (write-chunk-data buf (.-primary_bit_mask value) (.-chunk value))
     (varint-nbt-seq buf (.-block_entities value)))))

#_(gen-client
    "effect"
    :effect-id int32
    :position position
    :data int32
    :disable-relative-volume bool)

#_(gen-client
    "particle"
    :particle-id uint32
    :long-distance bool
    :position float32-xyz
    :offset float32-xyz)

(gen-client
  "join-game"
  :entity-id int32
  :gamemode (enum uint8 enums/gamemode)
  :dimension (enum int32 enums/dimension)
  :difficulty (enum uint8 enums/difficulty)
  :max-players uint8
  :level-type (string 16)
  :reduced-debug-info bool)

#_(gen-client
    "map")

(gen-client
  "entity-relative-move"
  :entity-id varint
  :delta int16-xyz
  :on-ground bool)

(gen-client
  "entity-look-and-relative-move"
  :entity-id varint
  :delta int16-xyz
  :rotation (tuple uint8 uint8)
  :on-ground bool)

(gen-client
  "entity-look"
  :entity-id varint
  :rotation (tuple uint8 uint8)
  :on-ground bool)

(gen-client
  "entity"
  :entity-id varint)

(gen-client
  "vehicle-move"
  :position float64-xyz
  :rotation float32-yaw-pitch)

(gen-client
  "open-sign-editor"
  :position position)

(gen-client
  "player-abilities"
  :flags (bitfield uint8 {:invulnerable 1
                          :flying 2
                          :allow-flying 3
                          :creative-mode 4})
  :flying-speed float32
  :field-of-view-modifier float32)

#_(gen-client
    "combat-event")

(gen-client
  "player-list-item"
  :action (enum varint {:add-player 0
                        :update-gamemode 1
                        :update-latency 2
                        :update-display-name 3
                        :remove-player 4})
  :players [:dispatch-on [:action]
            (->> {:add-player (composite
                                :profile (composite
                                           :unique-id uuid
                                           :username (string 16)
                                           :properties (sequence-with-length varint
                                                         (composite
                                                           :name (string 32767)
                                                           :value (string 32767)
                                                           :signature (optional (string 32767)))))
                                :gamemode (enum varint enums/gamemode)
                                :ping varint
                                :display-name (optional chat))
                  :update-gamemode (composite
                                     :unique-id uuid
                                     :gamemode (enum varint enums/gamemode))
                  :update-latency (composite
                                    :unique-id uuid
                                    :ping varint)
                  :update-display-name (composite
                                         :unique-id uuid
                                         :display-name (optional chat))
                  :remove-player (composite
                                   :unique-id uuid)}
                 (map (fn [[k v]] [k (sequence-with-length varint v)]))
                 (into {}))])

(gen-client
  "player-position-and-look"
  :position float64-xyz
  :rotation float32-yaw-pitch
  :flags (bitfield uint8 {:x 1
                          :y 2
                          :z 3
                          :pitch 4
                          :yaw 5})
  :teleport-id varint)

(gen-client
  "use-bed"
  :entity-id varint
  :position position)

(gen-client
  "destroy-entities"
  :entity-ids (sequence-with-length varint varint))

#_(gen-client
    "remove-entity-effect"
    :entity-id varint
    :effect-id uint8)

(gen-client
  "resource-pack-send"
  :url :string
  :hash :string)

(gen-client
  "respawn"
  :dimension (enum int32 enums/dimension)
  :difficulty (enum uint8 enums/difficulty)
  :gamemode (enum uint8 enums/gamemode)
  :level-type (string 16))

(gen-client
  "entity-head-look"
  :entity-id varint
  :head-yaw uint8)

#_(gen-client
    "world-border")

(gen-client
  "camera"
  :camera-id varint)

(gen-client
  "held-item-change"
  :slot uint8)

(gen-client
  "display-scoreboard"
  :position (enum uint8 {:list 0
                         :sidebar 1
                         :below-name 2})
  :score-name (string 16))

(gen-client
  "entity-metadata"
  :entity-id varint
  :metadata entity-metadata)

(gen-client
  "attach-entity"
  :attached-entity-id int32
  :holding-entity-id int32)

(gen-client
  "entity-velocity"
  :entity-id varint
  :velocity int16-xyz)

(gen-client
  "entity-equipment"
  :entity-id varint
  :slot (enum varint {:main-hand 0
                      :off-hand 1
                      :boots 2
                      :leggings 3
                      :chestplate 4
                      :helmet 5})
  :item slot)

(gen-client
  "set-experience"
  :experience-bar float32
  :level varint
  :total-experience varint)

(gen-client
  "update-health"
  :health float32
  :food varint
  :food-saturation float32)

(gen-client
  "scoreboard-objective"
  :objective-name (string 16)
  :mode (enum uint8 {:create-scoreboard 0
                     :remove-scoreboard 1
                     :update-display-text 2})
  :objective-value [:dispatch-on [:mode]
                    {:create-scoreboard (string 32)
                     :remove-scoreboard no-op
                     :update-display-text (string 32)}]
  :type [:dispatch-on [:mode]
         {:create-scoreboard (string 16)
          :remove-scoreboard no-op
          :update-display-text (string 16)}])

(gen-client
  "set-passengers"
  :entity-id varint
  :passengers (sequence-with-length varint varint))

#_(gen-client
    "teams")

(gen-client
  "update-score"
  :score-name (string 40)
  :action (enum uint8 {:create-or-update-item 0
                       :remove-item 1})
  :objective-name (string 16)
  :value [:dispatch-on [:action]
          {:create-or-update-item no-op
           :remove-item varint}])

(gen-client
  "spawn-position"
  :position position)

(gen-client
  "time-update"
  :world-age int64
  :time-of-day int64)

#_(gen-client
    "title")

#_(gen-client
    "sound-effect")

(gen-client
  "player-list-header-and-footer"
  :header chat
  :footer chat)

(gen-client
  "collect-item"
  :collected-entity-id varint
  :collector-entity-id varint
  :pickup-item-count varint)

(gen-client
  "entity-teleport"
  :entity-id varint
  :position float64-xyz
  :rotation (tuple uint8 uint8)
  :on-ground bool)

(gen-client
  "entity-properties"
  :entity-id varint
  :properties (sequence-with-length int32
                (composite
                  :key (string 64)
                  :value float64
                  :modifiers (sequence-with-length varint
                               (composite
                                 :uuid uuid
                                 :amount float64
                                 :operation uint8))))) ; TODO: operation enum

#_(gen-client
    "entity-effect"
    :entity-id varint
    :effect-id uint8
    :amplifier uint8
    :duration varint
    :flags (bitfield uint8 {:is-ambient 1
                            :show-particles 2}))
