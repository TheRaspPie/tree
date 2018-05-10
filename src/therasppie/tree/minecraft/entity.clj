(ns therasppie.tree.minecraft.entity
  (:require
    [better-cond.core :as b]
    [clojure.set :as set]
    [therasppie.tree.util.general :refer :all])
  (:import
    (java.util List)))

(def ^:private entities
  (->> "entities.edn"
       clojure.java.io/resource
       slurp
       clojure.edn/read-string
       (map (fn [entity] [(:id entity) entity]))
       (into {})))

(defn create-spawn-packet [entity]
  (b/cond
    :let [entity-type (::type entity)]
    (= :minecraft/xp_orb entity-type)
    {:packet-type :spawn-experience-orb
     :entity-id (::eid entity)
     :position (::position entity)
     :count (::xp_orb.count entity)}
    (= :minecraft/painting entity-type)
    {:packet-type :spawn-painting
     :rest ::TODO}
    :let [entity-type-descriptor (entities entity-type)]
    :let [object-id (:object-id entity-type-descriptor)]
    object-id
    {:packet-type :spawn-object
     :entity-id (::eid entity)
     :unique-id (::uid entity)
     :type object-id
     :position (::position entity)
     :rotation (::rotation entity)
     :data (case entity-type
             :minecraft/minecart 0
             :minecraft/chest_minecart 1
             :minecraft/furnace_minecart 2
             :minecraft/tnt_minecart 3
             :minecraft/spawner_minecart 4
             :minecraft/hopper_minecart 5
             :minecraft/commandblock_minecart 6
             :minecraft/item_frame (case (::item_frame.orientation entity)
                                     :south 0
                                     :west 1
                                     :north 2
                                     :east 3)
             0)
     :velocity [0 0 0]}
    :let [entity-id (:entity-id entity-type-descriptor)]
    {:packet-type :spawn-mob
     :entity-id (::eid entity)
     :unique-id (::uid entity)
     :type entity-id
     :position (::position entity)
     :rotation (::rotation entity)
     :head-pitch 0
     :velocity [0 0 0]
     :metadata []}))

(def init
  {})

(defn add-entity [entities entity]
  (assoc entities (::eid entity) entity))

(defn diff-entity [^List packets prev-entity next-entity]
  (if-not (and (= (::eid prev-entity) (::eid next-entity))
               (= (::uid prev-entity) (::uid next-entity))
               (= (::type prev-entity) (::type next-entity)))
    (do
      (.add packets
        {:packet-type :destroy-entities
         :entity-ids [(::eid prev-entity)]})
      (.add packets (create-spawn-packet next-entity)))
    (when-not (and (= (::position prev-entity) (::position next-entity))
                   (= (::rotation prev-entity) (::rotation next-entity)))
      (.add packets
        {:packet-type :entity-teleport
         :entity-id (::eid next-entity)
         :position (::position next-entity)
         :rotation (::rotation next-entity)
         :on-ground false}))))

(defn diff-entities [^List packets prev-entities next-entities]
  (let [prev-eids (set (keys prev-entities))
        next-eids (set (keys next-entities))
        removed-eids (set/difference prev-eids next-eids)
        added-eids (set/difference next-eids prev-eids)
        changed-eids (set/intersection prev-eids next-eids)]
    (.add packets
      {:packet-type :destroy-entities
       :entity-ids removed-eids})
    (run!
      #(.add packets (create-spawn-packet (next-entities %)))
      added-eids)
    (run!
      #(diff-entity packets (prev-entities %) (next-entities %))
      changed-eids)))
