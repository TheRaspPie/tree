(require 'therasppie.tree.clojure-fixes)

(ns therasppie.tree.main
  (:gen-class)
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [therasppie.tree.minecraft.player :as player]
    [therasppie.tree.minecraft.server :as server]
    [therasppie.tree.network.conn :as conn]
    [therasppie.tree.network.handler :as handler]
    [therasppie.tree.network.protocol :as protocol]
    [therasppie.tree.util.exception :as ex])
  (:import
    (java.lang Thread$UncaughtExceptionHandler)))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ t e]
      (println (str "Uncaught exception in thread " t ", shutting down JVM"))
      (.printStackTrace e)
      (.halt (Runtime/getRuntime) 1))))

(def cli-options
  [["-h" "--hostname hostname"]
   ["-p" "--port port"
    :parse-fn #(Long/parseLong %)
    :validate [#(<= 1 % 65535) "Not a number between 1 and 65535"]]
   ["-o" "--online-mode online-mode"
    :id :online-mode?
    :parse-fn #(Boolean/parseBoolean %)]])

(defn on-chat [player message]
  (if (str/starts-with? message "//")
    (let [throwable (volatile! nil)
          pl (atom player)
          result (try
                   (pr-str ((load-string (str
                                           "(in-ns 'therasppie.tree.main)"
                                           "(fn [player pl]"
                                           (subs message 2)
                                           ")"))
                             player pl))
                   (catch Throwable t (vreset! throwable t)))]
      (conn/send (::player/conn player)
        {:packet-type :chat-message
         :message {:text (if @throwable
                           (str "Error executing command: " (.getName (class @throwable)))
                           result)}
         :position :chat})
      @pl)
    player))

(def standard-options
  {:hostname "0.0.0.0"
   :port 25565
   :online-mode? false
   :on-join (fn [player]
              (conj player
                {::player/on-chat on-chat
                 ::player/on-move (fn [player position]
                                    (assoc-in player [::player/next-view :position] position))
                 ::player/on-rotate (fn [player _]
                                      player)}))})

(defn options->config [options]
  {:hostname (:hostname options)
   :port (:port options)
   :adapters {:online-mode? (constantly (:online-mode? options))
              :join (:on-join options)}
   :mk-serialize-fn (constantly (protocol/mk-serialize-fn protocol/handshaking))
   :mk-handle-fn handler/mk-handler})

(defn -main [& args]
  (let [{:keys [errors options]} (cli/parse-opts args cli-options)]
    (when errors
      (ex/throw-ex (str/join "\n" errors)))
    (server/start-server (options->config (conj standard-options options)))))
