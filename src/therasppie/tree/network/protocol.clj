(ns therasppie.tree.network.protocol
  (:require
    [therasppie.tree.network.packet.misc :refer :all]
    [therasppie.tree.network.packet.packet :as packet]
    [therasppie.tree.network.packet.play-client :refer :all]
    [therasppie.tree.network.packet.play-server :refer :all]
    [therasppie.tree.network.packet.play-shared :refer :all]
    [therasppie.tree.util.exception :as ex]))

(defn mk-serialize-fn [{in :in out :out}]
  (fn
    ([buf]
     (let [packet-id (packet/varint buf)
           packet-spec (in packet-id)]
       (when-not packet-spec
         (ex/throw-ex (str "Can't find packet id " packet-id)))
       (let [[packet-name decoder] packet-spec]
         (try
           (assoc (decoder buf) :packet-type packet-name)
           (catch Throwable ex
             (ex/throw-ex (str "Can't decode packet " packet-name) ex))))))
    ([buf packet]
     (let [packet-name (:packet-type packet)
           packet-spec (out packet-name)]
       (when-not packet-spec
         (ex/throw-ex (str "Can't find packet " packet-name)))
       (let [[packet-id encoder] packet-spec]
         (packet/varint buf packet-id)
         (try
           (encoder buf packet)
           (catch Throwable ex
             (ex/throw-ex (str "Can't encode packet " packet-name ": " packet) ex))))))))

(defmacro mk-proto [{in :in out :out}]
  {:in (into {} (map (fn [[packet-id packet-name]] [packet-id [(keyword packet-name) (symbol (str "server-" packet-name))]]) in))
   :out (into {} (map (fn [[packet-id packet-name]] [(keyword packet-name) [packet-id (symbol (str "client-" packet-name))]]) out))})

(def handshaking
  (mk-proto
    {:in {0 "handshake"}
     :out {}}))

(def status
  (mk-proto
    {:in {0 "request"
          1 "ping"}
     :out {0 "response"
           1 "pong"}}))

(def login
  (mk-proto
    {:in {0 "login-start"
          1 "encryption-response"}
     :out {0 "disconnect"
           1 "encryption-request"
           2 "login-success"
           3 "set-compression"}}))

(def play
  (mk-proto
    {:in {0 "teleport-confirm"
          1 "tab-complete"
          2 "chat-message"
          3 "client-status"
          4 "client-settings"
          5 "confirm-transaction"
          6 "enchant-item"
          7 "click-window"
          8 "close-window"
          9 "plugin-message"
          10 "use-entity"
          11 "keep-alive"
          12 "player"
          13 "player-position"
          14 "player-position-and-look"
          15 "player-look"
          16 "vehicle-move"
          17 "steer-boat"
          18 "craft-recipe-request"
          19 "player-abilities"
          20 "player-digging"
          21 "entity-action"
          22 "steer-vehicle"
          23 "crafting-book-data"
          24 "resource-pack-status"
          25 "advancement-tab"
          26 "held-item-change"
          27 "creative-inventory-action"
          28 "update-sign"
          29 "animation"
          30 "spectate"
          31 "player-block-placement"
          32 "use-item"}
     :out {0 "spawn-object"
           1 "spawn-experience-orb"
           2 "spawn-global-entity"
           3 "spawn-mob"
           4 "spawn-painting"
           5 "spawn-player"
           6 "animation"
           ;7 "statistics"
           8 "block-break-animation"
           ;9 "update-block-entity"
           ;10 "block-action"
           11 "block-change"
           ;12 "boss-bar"
           13 "server-difficulty"
           14 "tab-complete"
           15 "chat-message"
           16 "multi-block-change"
           17 "confirm-transaction"
           18 "close-window"
           19 "open-window"
           20 "window-items"
           ;21 "window-property"
           22 "set-slot"
           23 "set-cooldown"
           24 "plugin-message"
           ;25 "named-sound-effect"
           26 "disconnect"
           ;27 "entity-status"
           28 "explosion"
           29 "unload-chunk"
           30 "change-game-state"
           31 "keep-alive"
           32 "chunk-data"
           ;33 "effect"
           ;34 "particle"
           35 "join-game"
           ;36 "map"
           37 "entity"
           38 "entity-relative-move"
           39 "entity-look-and-relative-move"
           40 "entity-look"
           41 "vehicle-move"
           42 "open-sign-editor"
           ;43 "craft-recipe-response"
           44 "player-abilities"
           ;45 "combat-event"
           46 "player-list-item"
           47 "player-position-and-look"
           48 "use-bed"
           ;49 "unlock-recipes"
           50 "destroy-entities"
           ;51 "remove-entity-effect"
           52 "resource-pack-send"
           53 "respawn"
           54 "entity-head-look"
           ;55 "select-advancement-tab"
           ;56 "world-border"
           57 "camera"
           58 "held-item-change"
           59 "display-scoreboard"
           60 "entity-metadata"
           61 "attach-entity"
           62 "entity-velocity"
           63 "entity-equipment"
           64 "set-experience"
           65 "update-health"
           66 "scoreboard-objective"
           67 "set-passengers"
           ;68 "teams"
           69 "update-score"
           70 "spawn-position"
           71 "time-update"
           ;72 "title"
           ;73 "sound-effect"
           74 "player-list-header-and-footer"
           75 "collect-item"
           76 "entity-teleport"
           ;77 "advancements"
           78 "entity-properties"
           ;79 "entity-effect"
           }}))
