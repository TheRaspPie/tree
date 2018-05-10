(ns therasppie.tree.util.path
  (:refer-clojure :exclude [get resolve])
  (:import
    (java.nio.file DirectoryStream Files LinkOption Path Paths)
    (java.io File)))

(defn get ^Path [first & more]
  (Paths/get first (into-array String more)))

(defn resolve ^Path [^Path path ^String other]
  (.resolve path other))

(defn exists? [^Path path & options]
  (Files/exists path (into-array LinkOption options)))

(defn not-exists? [^Path path & options]
  (Files/notExists path (into-array LinkOption options)))

(defn to-file ^File [^Path path]
  (.toFile path))

(defn directory-stream ^DirectoryStream [^Path dir]
  (Files/newDirectoryStream dir))

(defn file-name ^Path [^Path path]
  (.getFileName path))
