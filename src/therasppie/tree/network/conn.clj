(ns therasppie.tree.network.conn
  (:refer-clojure :exclude [flush remove replace send sync])
  (:import
    (io.netty.bootstrap ServerBootstrap)
    (io.netty.channel Channel ChannelFutureListener ChannelHandler ChannelHandlerContext ChannelInboundHandlerAdapter ChannelInitializer ChannelOption SimpleChannelInboundHandler)
    (io.netty.channel.epoll Epoll EpollEventLoopGroup EpollServerSocketChannel)
    (io.netty.channel.nio NioEventLoopGroup)
    (io.netty.channel.socket.nio NioServerSocketChannel)
    (io.netty.handler.codec ByteToMessageCodec DecoderException)
    (io.netty.handler.timeout ReadTimeoutException ReadTimeoutHandler)
    (io.netty.util.concurrent Future GenericFutureListener)
    (java.io IOException)
    (java.util List)
    (therasppie.tree.network.codec LengthCodec)))

(defn add-first [^Channel channel ^String name ^ChannelHandler handler]
  (.addFirst (.pipeline channel) name handler)
  nil)

(defn add-last [^Channel channel ^String name ^ChannelHandler handler]
  (.addLast (.pipeline channel) name handler)
  nil)

(defn add-before [^Channel channel ^String base-name ^String name ^ChannelHandler handler]
  (.addBefore (.pipeline channel) base-name name handler)
  nil)

(defn add-after [^Channel channel ^String base-name ^String name ^ChannelHandler handler]
  (.addAfter (.pipeline channel) base-name name handler)
  nil)

(defn remove [^Channel channel ^String name]
  (.remove (.pipeline channel) name)
  nil)

(defn replace [^Channel channel ^String old-name ^String new-name ^ChannelHandler new-handler]
  (.replace (.pipeline channel) old-name new-name new-handler)
  nil)

(defn add-listener [^Future future ^GenericFutureListener listener]
  (.addListener future listener)
  nil)

(defn sync [^Future future]
  (.sync future)
  nil)

(defn listen-on [host port init-channel]
  (let [epoll (Epoll/isAvailable)
        group (if epoll (EpollEventLoopGroup.) (NioEventLoopGroup.))
        channel-class (if epoll EpollServerSocketChannel NioServerSocketChannel)]
    {:start (fn []
              (-> (ServerBootstrap.)
                  (.group group)
                  (.channel channel-class)
                  (.childOption ChannelOption/TCP_NODELAY true)
                  (.childHandler (proxy [ChannelInitializer] []
                                   (initChannel [ch] (init-channel ch))))
                  (.bind host port)
                  (sync))
              nil)
     :shutdown (fn []
                 (.shutdownGracefully group)
                 nil)
     :await (fn []
              (sync (.terminationFuture group))
              nil)}))

(defn connected? [^Channel channel]
  (.isActive channel))

(defn send [^Channel channel msg]
  (.writeAndFlush channel msg (.voidPromise channel))
  nil)

(defn send-no-flush [^Channel channel msg]
  (.write channel msg (.voidPromise channel))
  nil)

(defn flush [^Channel channel]
  (.flush channel)
  nil)

(defn send-and-close [^Channel channel msg]
  (.setAutoRead (.config channel) false)
  (.writeAndFlush channel msg
    (doto (.unvoid (.voidPromise channel))
      (add-listener ChannelFutureListener/CLOSE)))
  nil)

(defn close-immediately [^Channel channel]
  (.setAutoRead (.config channel) false)
  (.close channel)
  nil)

(defn- mk-codec [serialize]
  (proxy [ByteToMessageCodec] []
    (decode [_ in ^List out]
      (.add out (serialize in)))
    (encode [_ msg out]
      (serialize out msg))))

(defn- mk-handler [handle]
  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [_ msg]
      (handle msg))))

(defn- mk-exception-handler []
  (proxy [ChannelInboundHandlerAdapter] []
    (exceptionCaught [^ChannelHandlerContext ctx ^Throwable cause]
      (when-not (or (instance? IOException cause)
                    (instance? DecoderException cause)
                    (instance? ReadTimeoutException cause))
        (println "INTERNAL ERROR:")
        (.printStackTrace cause))
      (close-immediately (.channel ctx)))))

(defn mk-init-channel [mk-serialize-fn mk-handle-fn]
  #(doto %
     (add-last "read-timeout-handler" (ReadTimeoutHandler. 30))
     (add-last "length-codec" (LengthCodec.))
     (add-last "msg-codec" (mk-codec (mk-serialize-fn %)))
     (add-last "msg-handler" (mk-handler (mk-handle-fn %)))
     (add-last "exception-handler" (mk-exception-handler))))

(defn set-serialize-fn! [channel serialize-fn]
  (replace channel "msg-codec" "msg-codec" (mk-codec serialize-fn)))

(defn set-handle-fn! [channel handle-fn]
  (replace channel "msg-handler" "msg-handler" (mk-handler handle-fn)))
