(ns therasppie.tree.minecraft.world
  (:require
    [better-cond.core :as b]
    [clojure.string :as str]
    [therasppie.tree.util.exception :as ex]
    [therasppie.tree.util.path :as path])
  (:import
    (java.io BufferedInputStream DataInputStream RandomAccessFile)
    (java.nio.channels Channels)
    (java.util.zip InflaterInputStream)
    (therasppie.tree.util Arrays ChunkMap Nbt NibbleArray Vec3i)))

(deftype Chunk [biomes sections])

(def ^:const biome-count 256)
(def ^:const section-count 16)

(defn ->Chunk ^Chunk [biomes sections]
  (if-not (and (= biome-count (count biomes))
               (= section-count (count sections)))
    (ex/throw-arg "Invalid array length")
    (Chunk. biomes sections)))

(deftype ChunkSection [blocks blockLight skyLight])

(def ^:const block-count (* 16 16 16))
(def ^:const light-count (/ block-count 2))

(defn ->ChunkSection ^ChunkSection [blocks block-light sky-light]
  (if-not (and (= block-count (count blocks))
               (= light-count (count block-light))
               (= light-count (count sky-light)))
    (ex/throw-arg "Invalid array length")
    (ChunkSection. blocks block-light sky-light)))

(def ^ChunkSection empty-chunk-section
  (let [blocks (char-array block-count)
        light (byte-array light-count)]
    (->ChunkSection blocks light light)))

(def ^Chunk empty-chunk
  (let [biomes (byte-array biome-count)
        sections (object-array (repeat section-count empty-chunk-section))]
    (->Chunk biomes sections)))

;; ===== Chunk Loading Functions =====

(defn- read-section [section]
  (let [raw-blocks (get section "Blocks")
        add (get section "Add")
        add (when add (NibbleArray/of add))
        data (-> (get section "Data") (NibbleArray/of))
        blocks (aclone ^chars (.-blocks empty-chunk-section))]

    (dotimes [i (alength blocks)]
      (let [to-add (if-not add 0 (bit-shift-left (.nth ^NibbleArray add i) 8))
            block-id (bit-or to-add (Byte/toUnsignedInt (aget ^bytes raw-blocks i)))
            meta (.nth ^NibbleArray data i)
            block (char (bit-or (bit-shift-left block-id 4) meta))]
        (aset blocks i block)))

    (->ChunkSection blocks (get section "BlockLight") (get section "SkyLight"))))

(defn read-chunk-from-region-file
  "Tries to load chunk from a file. If the chunk is not available, returns nil.
  If the chunk is available, but malformed, throws an exception."
  [path region-chunk-loc]
  (b/cond
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
                      (Nbt/readNbt stream))]
            #_(when-not (s/valid? ::chunk nbt)
                (ex/throw-io (str "Invalid chunk: " (s/explain-str ::chunk nbt))))
            (let [level (get nbt "Level")
                  biomes (get level "Biomes")
                  sections (aclone ^objects (.-sections empty-chunk))]
              (run!
                (fn [section]
                  (let [section-y (get section "Y")]
                    (when-not (identical? empty-chunk-section (aget sections section-y))
                      (ex/throw-io (str "Duplicate chunk section: " section-y)))
                    (aset sections section-y (read-section section))))
                (get level "Sections"))
              (->Chunk biomes sections))))))))

(defn read-chunk-from-region-directory
  "Tries to load chunk from a directory. If the chunk is not available, returns nil.
  If the chunk is available, but malformed, throws an exception."
  [path chunk-loc]
  (let [region-loc (mapv #(bit-shift-right % 5) chunk-loc)
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
           section ^ChunkSection (aget ^objects (.-sections chunk) section-idx)
           idx (bit-or (.-x vec)
                       (bit-shift-left (.-z vec) 4)
                       (bit-shift-left (bit-and y 0xf) 8))]
       (aget ^chars (.-blocks section) idx))
     default-block))
  (^Chunk [^Chunk chunk ^Vec3i vec ^long block]
   (if (valid-chunk? vec)
     (let [y (.-y vec)
           section-idx (unsigned-bit-shift-right y 4)
           section ^ChunkSection (aget ^objects (.-sections chunk) section-idx)
           idx (bit-or (.-x vec)
                       (bit-shift-left (.-z vec) 4)
                       (bit-shift-left (bit-and y 0xf) 8))]
       (->Chunk (.-biomes chunk)
         (Arrays/assocO (.-sections chunk) section-idx
           (->ChunkSection
             (Arrays/assocC (.-blocks section) idx (char block))
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
