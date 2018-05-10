(ns therasppie.tree.network.session
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s])
  (:import
    (java.net URLEncoder)
    (java.nio.charset StandardCharsets)
    (java.util UUID)))

(defn- add-hyphens [s]
  (str (subs s 0 8) \- (subs s 8 12) \- (subs s 12 16) \- (subs s 16 20) \- (subs s 20 32)))

(defn- parse-uuid [s]
  (try
    (UUID/fromString s)
    (catch IllegalArgumentException _ nil)))

(s/def ::response (s/keys :req-un [::id ::name ::properties]
                          :only-specified? true))
(s/def ::id
  (s/and string?
         #(= 32 (count %))
         (s/conformer #(or (parse-uuid (add-hyphens %)) ::s/invalid))
         #(= 4 (.version ^UUID %))))
(s/def ::name string?)
(s/def ::properties (s/coll-of ::property))
(s/def ::property (s/keys :req-un [::name ::value]
                          :opt-un [::signature]
                          :only-specified? true))
(s/def ::value string?)
(s/def ::signature string?)

(defrecord GameProfile [unique-id username properties])
(defrecord Property [name value signature])

(defn- mk-profile [conformed]
  (->GameProfile
    (:id conformed)
    (:name conformed)
    (mapv
      #(->Property
         (:name %)
         (:value %)
         (:signature %))
      (:properties conformed))))

(defn has-joined [username hash]
  (let [encoding-name (.name StandardCharsets/UTF_8)
        url (str "https://sessionserver.mojang.com/session/minecraft/hasJoined"
                 "?username=" (URLEncoder/encode username encoding-name)
                 "&serverId=" (URLEncoder/encode hash encoding-name))
        response-text (slurp url :encoding encoding-name)
        response (json/read-str response-text :key-fn keyword)
        conformed (s/conform ::response response)]
    (when (s/invalid? conformed) (throw (Exception.)))
    (mk-profile conformed)))
