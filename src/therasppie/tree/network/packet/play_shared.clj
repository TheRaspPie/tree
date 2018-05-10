(ns therasppie.tree.network.packet.play-shared
  (:require
    [therasppie.tree.network.packet.packet :refer :all])
  (:import
    (io.netty.buffer ByteBuf)))

(let [keep-alive (composite
                   :keep-alive-id int64)]
  (def server-keep-alive keep-alive)
  (def client-keep-alive keep-alive))

(defn- byte-array-readable-bytes
  ([^ByteBuf buf]
   (let [value (byte-array (.readableBytes buf))]
     (.readBytes buf value)
     value))
  ([^ByteBuf buf ^bytes value]
   (.writeBytes buf value)))

(let [plugin-message (composite
                       :channel (string 20)
                       :data byte-array-readable-bytes)]
  (def server-plugin-message plugin-message)
  (def client-plugin-message plugin-message))
