(ns therasppie.tree.network.packet.misc
  (:require
    [therasppie.tree.network.packet.packet :refer :all]))

(gen-server
  "handshake"
  :protocol-version varint
  :server-address (string 255)
  :server-port uint16
  :next-state varint)

(gen-server
  "login-start"
  :username (string 16))

(gen-client
  "encryption-request"
  :server-id (string 20)
  :public-key (byte-arr Integer/MAX_VALUE)
  :verify-token (byte-arr Integer/MAX_VALUE))

(gen-server
  "encryption-response"
  :shared-secret (byte-arr Integer/MAX_VALUE)
  :verify-token (byte-arr Integer/MAX_VALUE))

(gen-client
  "login-success"
  :uuid (string 36)
  :username (string 16))

(gen-client
  "set-compression"
  :threshold varint)

(gen-server
  "request")

(gen-client
  "response"
  :response json)

(gen-server
  "ping"
  :payload int64)

(gen-client
  "pong"
  :payload int64)

(gen-client
  "disconnect"
  :reason chat)
