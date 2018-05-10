(ns therasppie.tree.util.nbt
  (:require
    [better-cond.core :as b]
    [therasppie.tree.util.exception :as ex]
    [therasppie.tree.util.general :refer [run-kv!]])
  (:import
    (java.io DataInput DataOutput)
    (therasppie.tree.util IPersistentByteArray IPersistentIntArray PersistentByteArray PersistentIntArray)))

(def ^:const ^:private end-tag 0)
(def ^:const ^:private compound-tag 10)

(def byte-array? (partial instance? IPersistentByteArray))
(def int-array? (partial instance? IPersistentIntArray))

(declare read-compound-tag read-list-tag)

(defn- read-tag [^DataInput input ^long id]
  (case (int id) ;; Don't remove cast to int - compiles into a tableswitch
    4 (.readLong input)
    3 (long (.readInt input))
    2 (long (.readShort input))
    1 (long (.readByte input))
    6 (.readDouble input)
    5 (double (.readFloat input))
    8 (.readUTF input)
    10 (read-compound-tag input)
    9 (read-list-tag input)
    7 (let [count (.readInt input)]
        (loop [array (.asTransient (PersistentByteArray/create count))
               i 0]
          (if (< i count)
            (recur (.assocByte array i (.readByte input)) (inc i))
            (.persistent array))))
    11 (let [count (.readInt input)]
         (loop [array (.asTransient (PersistentIntArray/create count))
                i 0]
           (if (< i count)
             (recur (.assocInt array i (.readInt input)) (inc i))
             (.persistent array))))
    (ex/throw-io (str "Invalid tag id: " id))))

(defn- read-id ^long [^DataInput input]
  (long (.readByte input)))

(defn- read-compound-tag [^DataInput input]
  (loop [m (transient {})]
    (let [id (read-id input)]
      (if (= end-tag id)
        (persistent! m)
        (let [name (keyword (.readUTF input))
              tag (read-tag input id)]
          (recur (assoc! m name tag)))))))

(defn- read-list-tag [^DataInput input]
  (let [element-id (read-id input)
        size (.readInt input)]
    (when (neg? size) (ex/throw-io (str "Negative list tag size: " size)))
    (loop [remaining size
           m (transient [])]
      (if (zero? remaining)
        (persistent! m)
        (recur (dec remaining) (conj! m (read-tag input element-id)))))))

(declare write-compound-tag write-list-tag)

(defn- write-tag [^DataOutput output ^long id tag]
  (case (int id) ;; Don't remove cast to int - compiles into a tableswitch
    4 (.writeLong output tag)
    3 (.writeInt output tag)
    2 (.writeShort output tag)
    1 (.writeByte output tag)
    6 (.writeDouble output tag)
    5 (.writeFloat output tag)
    8 (.writeUTF output tag)
    10 (write-compound-tag output tag)
    9 (write-list-tag output tag)
    7 (let [length (count tag)]
        (.writeInt output length)
        (dotimes [i length]
          (.writeByte output (.nthByte ^IPersistentByteArray tag i))))
    11 (let [length (count tag)]
         (.writeInt output length)
         (dotimes [i length]
           (.writeInt output (.nthInt ^IPersistentIntArray tag i))))))

(defn- write-id [^DataOutput output ^long id]
  (.writeByte output id))

(defn- get-id ^long [tag]
  (b/cond
    (int? tag) 4
    (float? tag) 6
    (string? tag) 8
    (map? tag) 10
    (vector? tag) 9
    (byte-array? tag) 7
    (int-array? tag) 11
    (ex/throw-io (str "Not a tag: " tag))))

(defn- write-compound-tag [^DataOutput output tag]
  (run-kv!
    (fn [key val]
      (when-not (simple-keyword? key)
        (ex/throw-io (str "Compound tag keys must be simple keywords: " tag)))
      (let [id (get-id val)]
        (write-id output id)
        (.writeUTF output (name key))
        (write-tag output id val)))
    tag)
  (.writeByte output end-tag))

(defn- write-list-tag [^DataOutput output tag]
  (let [size (count tag)
        element-id (if (zero? size) end-tag (get-id (nth tag 0)))]
    (write-id output element-id)
    (.writeInt output size)
    (run!
      (fn [element]
        (when-not (= element-id (get-id element))
          (ex/throw-io (str "List tag elements must be of the same type: " tag)))
        (write-tag output element-id element))
      tag)))

(defn read-nbt [^DataInput input]
  (let [id (read-id input)]
    (when-not (= compound-tag id) (ex/throw-io (str "Expected compound tag, got id: " id)))
    (.readUTF input)
    (read-tag input id)))

(defn write-nbt [^DataOutput output tag]
  (let [id (get-id tag)]
    (when-not (= compound-tag id) (ex/throw-io (str "Expected compound tag, got tag: " tag)))
    (write-id output compound-tag)
    (.writeUTF output "")
    (write-tag output id tag)))
