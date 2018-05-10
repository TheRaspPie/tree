(ns therasppie.tree.network.packet.play-server
  (:require
    [therasppie.tree.network.packet.enums :as enums]
    [therasppie.tree.network.packet.packet :refer :all]))

(gen-server
  "teleport-confirm"
  :teleport-id varint)

(let [entries (sequence-with-length int16
                (composite
                  :item slot
                  :crafting-slot int8
                  :player-slot int8))]
  (gen-server
    "prepare-crafting-grid"
    :window-id uint8
    :action-number int16
    :return-entries entries
    :prepare-entries entries))

(gen-server
  "tab-complete"
  :text (string 32767)
  :assume-command bool
  :looked-at-block (optional position))

(gen-server
  "chat-message"
  :message (string 256))

(gen-server
  "client-status"
  :action (enum varint {:perform-respawn 0
                        :request-stats 1
                        :open-inventory 2}))

(gen-server
  "client-settings"
  :locale (string 16)
  :view-distance int8
  :chat-mode (enum varint {:enabled 0
                           :commands-only 1
                           :hidden 2})
  :chat-colors bool
  :displayed-skin-parts (bitfield uint8 {:cape 1
                                         :jacket 2
                                         :left-sleeve 3
                                         :right-sleeve 4
                                         :left-pants-leg 5
                                         :right-pants-leg 6
                                         :hat 7})
  :main-hand (enum varint {:left 0
                           :right 1}))

(gen-server
  "confirm-transaction"
  :window-id uint8
  :action-number int16
  :accepted bool)

(gen-server
  "enchant-item"
  :window-id uint8
  :enchantment int8)

(gen-server
  "click-window"
  :window-id uint8
  :slot int16
  :button int8
  :action-number int16
  :mode (enum varint {:mouse-click 0
                      :shift-mouse-click 1
                      :number-key 2
                      :middle-click 3
                      :drop 4
                      :mouse-drag 5
                      :double-click 6})
  :clicked-item slot)

(gen-server
  "close-window"
  :window-id uint8)

(gen-server
  "use-entity"
  :target varint
  :type (enum varint {:interact 0
                      :attack 1
                      :interact-at 2})
  :target-position [:dispatch-on [:type]
                    {:interact no-op
                     :attack no-op
                     :interact-at float32-xyz}]
  :hand [:dispatch-on [:type]
         (let [hand (enum varint enums/hand)]
           {:interact hand
            :attack no-op
            :interact-at hand})])

(gen-server
  "player"
  :on-ground bool)

(gen-server
  "player-position"
  :position float64-xyz
  :on-ground bool)

(gen-server
  "player-position-and-look"
  :position float64-xyz
  :rotation float32-yaw-pitch
  :on-ground bool)

(gen-server
  "player-look"
  :rotation float32-yaw-pitch
  :on-ground bool)

(gen-server
  "vehicle-move"
  :position float64-xyz
  :rotation float32-yaw-pitch)

(gen-server
  "steer-boat"
  :right-paddle-turning bool
  :left-paddle-turning bool)

(gen-server
  "craft-recipe-request"
  :window-id uint8
  :recipe varint
  :make-all? bool)

(gen-server
  "player-abilities"
  :flags (bitfield uint8 {:is-creative 1
                          :is-flying 2
                          :can-fly 3
                          :damage-disabled 4})
  :flying-speed float32
  :walking-speed float32)

(gen-server
  "player-digging"
  :status (enum varint {:started-digging 0
                        :cancelled-digging 1
                        :finished-digging 2
                        :drop-item-stack 3
                        :drop-item 4
                        :shoot-arrow-or-finish-eating 5
                        :swap-item-in-hand 6})
  :position position
  :face (enum int8 enums/face))

(gen-server
  "entity-action"
  :entity-id varint
  :action (enum varint {:start-sneaking 0
                        :stop-sneaking 1
                        :leave-bed 2
                        :start-sprinting 3
                        :stop-sprinting 4
                        :start-jump-with-horse 5
                        :stop-jump-with-horse 6
                        :open-horse-inventory 7
                        :start-flying-with-elytra 8})
  :jump-boost varint)

(gen-server
  "steer-vehicle"
  :sideways float32
  :forward float32
  :flags (bitfield uint8 {:jump 1
                          :unmount 2}))

(gen-server
  "crafting-book-data"
  :type (enum varint {:displayed-recipe 0
                      :crafting-book-status 1})
  :data [:dispatch-on [:type]
         {:displayed-recipe (composite
                              :recipe-id uint32)
          :crafting-book-status (composite
                                  :crafting-book-open bool
                                  :crafting-filter bool)}])

(gen-server
  "resource-pack-status"
  :result (enum varint {:successfully-loaded 0
                        :declined 1
                        :failed-download 2
                        :accepted 3}))

(gen-server
  "advancement-tab"
  :action (enum varint {:opened-tab 0
                        :closed-screen 1})
  :tab-id [:dispatch-on [:action]
           {:opened-tab varint
            :closed-screen no-op}])

(gen-server
  "held-item-change"
  :slot int16)

(gen-server
  "creative-inventory-action"
  :slot int16
  :clicked-item slot)

(gen-server
  "update-sign"
  :position position
  :lines (tuple (string 384) (string 384) (string 384) (string 384)))

(gen-server
  "animation"
  :hand (enum varint enums/hand))

(gen-server
  "spectate"
  :target-player uuid)

(gen-server
  "player-block-placement"
  :position position
  :face (enum varint enums/face)
  :hand (enum varint enums/hand)
  :cursor-position float32-xyz)

(gen-server
  "use-item"
  :hand (enum varint enums/hand))
