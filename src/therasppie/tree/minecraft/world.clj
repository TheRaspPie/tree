(ns therasppie.tree.minecraft.world
  (:require
    [better-cond.core :as b]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [therasppie.tree.util.exception :as ex]
    [therasppie.tree.util.nbt :as nbt]
    [therasppie.tree.util.path :as path])
  (:import
    (clojure.lang IPersistentVector)
    (java.io BufferedInputStream DataInputStream RandomAccessFile)
    (java.nio.channels Channels)
    (java.util.zip InflaterInputStream)
    (therasppie.tree.util ChunkMap IPersistentCharArray PersistentByteArray PersistentCharArray PrimitiveArray Vec3i Arrays)))

(deftype Chunk [biomes sections])

(def ^:private ^:const biome-count 256)
(def ^:private ^:const section-count 16)

(defn ->Chunk ^Chunk [biomes sections]
  (if-not (and (= biome-count (count biomes))
               (= section-count (count sections)))
    (ex/throw-arg "Invalid array length")
    (Chunk. biomes sections)))

(deftype ChunkSection [blocks blockLight skyLight])

(def ^:private ^:const block-count (* 16 16 16))
(def ^:private ^:const light-count (/ block-count 2))

(defn ->ChunkSection ^ChunkSection [blocks block-light sky-light]
  (if-not (and (= block-count (count blocks))
               (= light-count (count block-light))
               (= light-count (count sky-light)))
    (ex/throw-arg "Invalid array length")
    (ChunkSection. blocks block-light sky-light)))

(def ^ChunkSection empty-chunk-section
  (let [blocks (PersistentCharArray/create block-count)
        light (PersistentByteArray/create light-count)]
    (->ChunkSection blocks light light)))

(def ^Chunk empty-chunk
  (let [biomes (PersistentByteArray/create biome-count)
        sections (vec (repeat section-count empty-chunk-section))]
    (->Chunk biomes sections)))

(defn- nth-prim ^long [^PrimitiveArray array ^long index]
  (.nthPrim array index))

;; ===== Chunk Specs =====

(s/def ::Y (s/int-in 0 16))
(s/def ::Blocks nbt/byte-array?)
(s/def ::Add nbt/byte-array?)
(s/def ::Data nbt/byte-array?)
(s/def ::BlockLight nbt/byte-array?)
(s/def ::SkyLight nbt/byte-array?)
(s/def ::section (s/keys :req-un [::Y ::Blocks ::Data ::BlockLight ::SkyLight] :opt-un [::Add]))

(s/def ::chunk (s/keys :req-un [::Level]))
(s/def ::Level (s/keys :req-un [::Biomes ::Sections]))
(s/def ::Biomes nbt/byte-array?)
(s/def ::Sections (s/coll-of ::section))

;; ===== Chunk Loading Functions =====

(defn- read-section [section]
  (let [blocks (:Blocks section)
        add (some-> (:Add section) (Arrays/asNibbleArray))
        data (-> (:Data section) (Arrays/asNibbleArray))
        count (count blocks)
        persistent-blocks
        (loop [array (.asTransient ^IPersistentCharArray (.-blocks empty-chunk-section))
               i 0]
          (if (< i count)
            (let [to-add (if-not add 0 (bit-shift-left (nth-prim add i) 8))
                  block-id (bit-or (Byte/toUnsignedInt (nth-prim blocks i)) to-add)
                  meta (nth-prim data i)
                  block (char (bit-or (bit-shift-left block-id 4) meta))]
              (recur (.assocChar array i block) (inc i)))
            (.persistent array)))]
    (->ChunkSection persistent-blocks (:BlockLight section) (:SkyLight section))))

(s/def ::region-chunk-loc (s/tuple (s/int-in 0 32) (s/int-in 0 32)))
(defn read-chunk-from-region-file
  "Tries to load chunk from a file. If the chunk is not available, returns nil.
  If the chunk is available, but malformed, throws an exception."
  [path region-chunk-loc]
  (b/cond
    :let [region-chunk-loc (let [x (s/conform ::region-chunk-loc region-chunk-loc)]
                             (if (s/invalid? x)
                               (ex/throw-io (str "Invalid region chunk loc: " (s/explain-str ::region-chunk-loc region-chunk-loc)))
                               x))]
    :when (path/exists? path)
    (with-open [file (RandomAccessFile. (path/to-file path) "r")]
      (.seek file (* 4 (+ (region-chunk-loc 0) (* 32 (region-chunk-loc 1)))))
      (let [offset (bit-or (-> file .readUnsignedByte (bit-shift-left 16))
                           (-> file .readUnsignedByte (bit-shift-left 8))
                           (-> file .readUnsignedByte))
            size-in-sectors (.readUnsignedByte file)]
        (when-not (and (zero? offset) (zero? size-in-sectors))
          (when (< offset 2) (ex/throw-io (str "Invalid offset " offset " for chunk " region-chunk-loc)))

          (.seek file (* 4096 offset))
          (.skipBytes file 5) ;; size and compression format
          (let [nbt (with-open [stream (-> (.getChannel file)
                                           (Channels/newInputStream)
                                           (InflaterInputStream.)
                                           (BufferedInputStream.)
                                           (DataInputStream.))]
                      (nbt/read-nbt stream))]
            (when-not (s/valid? ::chunk nbt)
              (ex/throw-io (str "Invalid chunk: " (s/explain-str ::chunk nbt))))
            (let [level (:Level nbt)
                  biomes (:Biomes level)
                  sections (reduce
                             (fn [sections section]
                               (let [section-y (:Y section)]
                                 (if-not (identical? (sections section-y) empty-chunk-section)
                                   (ex/throw-io (str "Duplicate chunk section: " section-y))
                                   (assoc sections section-y (read-section section)))))
                             (.-sections empty-chunk) (:Sections level))]
              (->Chunk biomes sections))))))))

(s/def ::chunk-loc (s/tuple int? int?))
(defn read-chunk-from-region-directory
  "Tries to load chunk from a directory. If the chunk is not available, returns nil.
  If the chunk is available, but malformed, throws an exception."
  [path chunk-loc]
  (let [chunk-loc (let [x (s/conform ::chunk-loc chunk-loc)]
                    (if (s/invalid? x)
                      (ex/throw-io (str "Invalid chunk loc: " (s/explain-str ::chunk-loc chunk-loc)))
                      x))
        region-loc (mapv #(bit-shift-right % 5) chunk-loc)
        region-chunk-loc (mapv #(bit-and % 2r11111) chunk-loc)]
    (read-chunk-from-region-file
      (path/resolve path (str "r." (region-loc 0) "." (region-loc 1) ".mca"))
      region-chunk-loc)))

(defn- parse-int [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _ nil)))

(defn read-all-chunks-from-region-directory
  ^ChunkMap [^ChunkMap chunks path]
  (doseq [path (path/directory-stream path)]
    (b/cond
      :let [file-name (str (path/file-name path))
            parts (str/split file-name #"\.")]
      :when (and (= 4 (count parts))
                 (= "r" (parts 0))
                 (= "mca" (parts 3)))
      :let [region-x (parse-int (parts 1))
            region-z (parse-int (parts 2))]
      :when (and region-x region-z)
      (dotimes [region-chunk-x 32]
        (dotimes [region-chunk-z 32]
          (b/cond
            :let [chunk (read-chunk-from-region-file path [region-chunk-x region-chunk-z])]
            :when chunk
            :let [chunk-x (+ (* 32 region-x) region-chunk-x)
                  chunk-z (+ (* 32 region-z) region-chunk-z)]
            (.assoc chunks chunk-x chunk-z chunk))))))
  chunks)

;; ===== Chunk Access =====

(defn- valid-chunk? [^Vec3i vec]
  (let [x (.-x vec)
        y (.-y vec)
        z (.-z vec)]
    (and (= x (bit-and x 0xf))
         (= z (bit-and z 0xf))
         (= y (bit-and y 0xff)))))

(def ^:private ^:const default-block 0)

(defn raw-chunk-block
  (^long [^Chunk chunk ^Vec3i vec]
   (if (valid-chunk? vec)
     (let [y (.-y vec)
           section-idx (unsigned-bit-shift-right y 4)
           section ^ChunkSection (nth (.-sections chunk) section-idx)
           idx (bit-or (.-x vec)
                       (bit-shift-left (.-z vec) 4)
                       (bit-shift-left (bit-and y 0xf) 8))]
       (nth-prim (.-blocks section) idx))
     default-block))
  (^Chunk [^Chunk chunk ^Vec3i vec ^long block]
   (if (valid-chunk? vec)
     (let [y (.-y vec)
           section-idx (unsigned-bit-shift-right y 4)
           section ^ChunkSection (nth (.-sections chunk) section-idx)
           idx (bit-or (.-x vec)
                       (bit-shift-left (.-z vec) 4)
                       (bit-shift-left (bit-and y 0xf) 8))]
       (->Chunk (.-biomes chunk)
         (.assocN ^IPersistentVector (.-sections chunk) section-idx
           (->ChunkSection
             (.assocChar ^IPersistentCharArray (.-blocks section) idx (char block))
             (.-blockLight section)
             (.-skyLight section)))))
     chunk)))

(defn raw-block
  (^long [^ChunkMap chunks ^Vec3i vec]
   (let [x (.-x vec)
         z (.-z vec)
         chunk (.get chunks (bit-shift-right x 4) (bit-shift-right z 4) empty-chunk)]
     (raw-chunk-block chunk (Vec3i. (bit-and x 0xf) (.-y vec) (bit-and z 0xf)))))
  (^ChunkMap [^ChunkMap chunks ^Vec3i vec ^long block]
   (let [x (.-x vec)
         z (.-z vec)
         chunk-x (bit-shift-right x 4)
         chunk-z (bit-shift-right z 4)
         chunk (.get chunks chunk-x chunk-z empty-chunk)]
     (.assoc chunks chunk-x chunk-z
       (raw-chunk-block chunk (Vec3i. (bit-and x 0xf) (.-y vec) (bit-and z 0xf)) block)))
   chunks))
