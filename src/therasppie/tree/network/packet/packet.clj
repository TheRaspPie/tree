(ns therasppie.tree.network.packet.packet
  (:require
    [clojure.data.json :as json])
  (:import
    (io.netty.buffer ByteBuf ByteBufInputStream)
    (java.nio.charset StandardCharsets)
    (java.util UUID)
    (therasppie.tree.network.codec VarIntUtil)
    (therasppie.tree.network.packet Prims)
    (therasppie.tree.util Nbt)))

(defn- inline-fn [read-sym write-sym]
  (fn
    ([buf] (list read-sym buf))
    ([buf value] (list write-sym buf value))))

(defn int64
  {:inline (inline-fn `Prims/readInt64 `Prims/writeInt64)}
  (^long [buf] (Prims/readInt64 buf))
  ([buf ^long value] (Prims/writeInt64 buf value)))

(defn uint32
  {:inline (inline-fn `Prims/readUInt32 `Prims/writeUInt32)}
  (^long [buf] (Prims/readUInt32 buf))
  ([buf ^long value] (Prims/writeUInt32 buf value)))

(defn int32
  {:inline (inline-fn `Prims/readInt32 `Prims/writeInt32)}
  (^long [buf] (Prims/readInt32 buf))
  ([buf ^long value] (Prims/writeInt32 buf value)))

(defn uint16
  {:inline (inline-fn `Prims/readUInt16 `Prims/writeUInt16)}
  (^long [buf] (Prims/readUInt16 buf))
  ([buf ^long value] (Prims/writeUInt16 buf value)))

(defn int16
  {:inline (inline-fn `Prims/readInt16 `Prims/writeInt16)}
  (^long [buf] (Prims/readInt16 buf))
  ([buf ^long value] (Prims/writeInt16 buf value)))

(defn uint8
  {:inline (inline-fn `Prims/readUInt8 `Prims/writeUInt8)}
  (^long [buf] (Prims/readUInt8 buf))
  ([buf ^long value] (Prims/writeUInt8 buf value)))

(defn int8
  {:inline (inline-fn `Prims/readInt8 `Prims/writeInt8)}
  (^long [buf] (Prims/readInt8 buf))
  ([buf ^long value] (Prims/writeInt8 buf value)))

(defn float64
  {:inline (inline-fn `Prims/readFloat64 `Prims/writeFloat64)}
  (^double [buf] (Prims/readFloat64 buf))
  ([buf ^double value] (Prims/writeFloat64 buf value)))

(defn float32
  {:inline (inline-fn `Prims/readFloat32 `Prims/writeFloat32)}
  (^double [buf] (Prims/readFloat32 buf))
  ([buf ^double value] (Prims/writeFloat32 buf value)))

(defn bool
  {:inline (inline-fn `Prims/readBool `Prims/writeBool)}
  ([buf] (Prims/readBool buf))
  ([buf value] (Prims/writeBool buf value)))

(defn varint
  {:inline (inline-fn `VarIntUtil/readVarInt `VarIntUtil/writeVarInt)}
  (^long [buf] (VarIntUtil/readVarInt buf))
  ([buf ^long value] (VarIntUtil/writeVarInt buf value)))

(defn uuid
  ([buf]
   (UUID. (int64 buf) (int64 buf)))
  ([buf ^UUID value]
   (int64 buf (.getMostSignificantBits value))
   (int64 buf (.getLeastSignificantBits value))))

(defn byte-arr [^long n]
  (fn
    ([^ByteBuf buf]
     (let [count (varint buf)]
       (if (<= count n)
         (let [value (byte-array count)]
           (.readBytes buf value)
           value)
         (throw (Exception. (str "Byte array too long, expected: " n ", got: " count))))))
    ([^ByteBuf buf ^bytes value]
     (let [count (alength value)]
       (if (<= count n)
         (do
           (varint buf count)
           (.writeBytes buf value))
         (throw (Exception. (str "Byte array too long, expected: " n ", got: " count))))))))

(defn string [^long n]
  (let [byte-arr (byte-arr (* 4 n))]
    (fn
      ([buf]
       (let [value (String. ^bytes (byte-arr buf) StandardCharsets/UTF_8)
             count (.length value)]
         (if (<= count n)
           value
           (throw (Exception. (str "String too long, expected: " n ", got: " count))))))
      ([buf ^String value]
       (let [count (.length value)]
         (if (<= count n)
           (byte-arr buf (.getBytes value StandardCharsets/UTF_8))
           (throw (Exception. (str "String too long, expected: " n ", got: " count)))))))))

(let [string (string 32767)]
  (defn json
    ([buf]
     (json/read-str (string buf)))
    ([buf value]
     (string buf (json/write-str value)))))

(def chat json)

(defn nbt
  ([buf]
   (Nbt/readNbt (ByteBufInputStream. buf)))
  #_([buf value]
      (Nbt/writeNbt (ByteBufOutputStream. buf) value)))

(defn optional [serialize]
  (fn
    ([buf]
     (when (bool buf)
       (serialize buf)))
    ([buf value]
     (let [present? (some? value)]
       (bool buf present?)
       (when present?
         (serialize buf value))))))

(defn sequence-with-length [serialize-length serialize]
  (fn
    ([buf]
     (into [] (repeatedly (serialize-length buf) #(serialize buf))))
    ([buf value]
     (serialize-length buf (count value))
     (run! #(serialize buf %) value))))

(defn sequence-with-end [serialize-end end-value serialize]
  (fn
    ([^ByteBuf buf]
     (loop [res (transient [])]
       (let [reader-idx (.readerIndex buf)]
         (if (= end-value (serialize-end buf))
           (persistent! res)
           (do
             (.readerIndex buf reader-idx)
             (recur (conj! res (serialize buf))))))))
    ([buf value]
     (run! #(serialize buf %) value)
     (serialize-end buf end-value))))

(defn enum [serialize codec]
  (let [name-to-num (into {} codec)
        num-to-name (into {} (map (fn [[name num]] [num name]) name-to-num))]
    (when-not (= (count name-to-num) (count num-to-name))
      (throw (IllegalArgumentException. (str "Map isn't a bijective map: " name-to-num))))
    (fn
      ([buf]
       (let [value (serialize buf)]
         (or (num-to-name value)
             (throw (Exception. (str "Couldn't decode enum, value: " value ", map: " num-to-name))))))
      ([buf value]
       (if-let [num (name-to-num value)]
         (serialize buf num)
         (throw (Exception. (str "Couldn't encode enum, value: " value ", map: " name-to-num))))))))

(defn- bits->set [codec ^long bits]
  (let [xf (comp
             (filter #(let [flag (bit-shift-left 1 (long (nth % 1)))]
                        (= flag (bit-and flag bits))))
             (map #(nth % 0)))]
    (into #{} xf codec)))

(defn- set->bits [codec set]
  (reduce
    (fn [^long bits val]
      (let [flag (bit-shift-left 1 (long (codec val)))]
        (bit-or flag bits)))
    0
    set))

(defn bitfield [serialize codec]
  (fn
    ([buf] (bits->set codec (serialize buf)))
    ([buf value] (serialize buf (set->bits codec value)))))

(defn position
  ([buf]
   (let [value (int64 buf)]
     [(bit-shift-right value 38)
      (-> value (bit-shift-right 26) (bit-and 0xff))
      (-> value (bit-shift-left 38) (bit-shift-right 38))]))
  ([buf pos]
   (when-not (= 3 (count pos))
     (throw (Exception. (str "Invalid position: " pos))))
    ;; Only allow longs
   (let [x (.longValue ^Long (nth pos 0))
         y (.longValue ^Long (nth pos 1))
         z (.longValue ^Long (nth pos 2))
         value (bit-or
                 (-> x (bit-and 0x3ffffff) (bit-shift-left 38))
                 (-> y (bit-and 0xfff) (bit-shift-left 26))
                 (bit-and z 0x3ffffff))]
     (int64 buf value))))

(defn slot
  ([^ByteBuf buf]
   (let [item-id (int16 buf)]
     (when-not (= item-id -1)
       (let [count (int8 buf)
             damage (int16 buf)
             nbt (when-not (zero? (int8 buf))
                   (.readerIndex buf (dec (.readerIndex buf)))
                   (nbt buf))]
         {:item-id item-id
          :count count
          :damage damage
          :nbt nbt})))))

(defn no-op
  ([_] nil)
  ([_ _]))

(defn tuple [& serializers]
  (let [serializers (into [] serializers)
        serializers-count (count serializers)]
    (fn
      ([buf]
       (mapv #(% buf) serializers))
      ([buf value]
       (when-not (= serializers-count (count value))
         (throw (Exception. (str "Invalid tuple: " value))))
       (dotimes [i serializers-count]
         ((nth serializers i) buf (nth value i)))))))

(defn- composite-impl [packet-desc]
  (let [buf (gensym "buf__")
        value (gensym "value__")
        desc-map (apply array-map packet-desc)
        desc-keys (keys desc-map)
        symbols (into {} (map (fn [key] [key (gensym)]) desc-keys))
        let-bindings (->> desc-map
                          (map (fn [[key producer]] [(symbols key) (if (vector? producer) (producer 2) producer)]))
                          (apply concat))]
    `(let [~@let-bindings]
       (fn
         ([~buf]
          (let [~@(->> desc-map
                       (map (fn [[key producer]]
                              (let [read-form (if (vector? producer)
                                                (list* (symbols key) (map symbols (producer 1)))
                                                (symbols key))]
                                [(symbols key) `(~read-form ~buf)])))
                       (apply concat))]
            ~(->> desc-keys
                  (map (fn [key] [key (symbols key)]))
                  (into {}))))
         ([~buf ~value]
          ~@(map
              (fn [[key producer]]
                (let [write-form (if (vector? producer)
                                   (list* (symbols key) (map #(list % value) (producer 1)))
                                   (symbols key))]
                  `(~write-form ~buf (~key ~value))))
              desc-map))))))

(defmacro composite [& packet-desc]
  (composite-impl packet-desc))

(def float64-xyz
  (tuple float64 float64 float64))

(def float32-xyz
  (tuple float32 float32 float32))

(def int16-xyz
  (tuple int16 int16 int16))

(def float32-yaw-pitch
  (tuple float32 float32))

(defmacro gen-server [packet-name & packet-desc]
  `(def ~(symbol (str "server-" packet-name)) ~(composite-impl packet-desc)))

(defmacro gen-client [packet-name & packet-desc]
  `(def ~(symbol (str "client-" packet-name)) ~(composite-impl packet-desc)))
