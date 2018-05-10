(ns therasppie.tree.util.exception
  (:import
    (java.io IOException)))

(defn throw-ex
  ([] (throw (Exception.)))
  ([message] (throw (Exception. ^String message)))
  ([message cause] (throw (Exception. ^String message ^Throwable cause))))

(defn throw-arg
  ([] (throw (IllegalArgumentException.)))
  ([message] (throw (IllegalArgumentException. ^String message)))
  ([message cause] (throw (IllegalArgumentException. ^String message ^Throwable cause))))

(defn throw-state
  ([] (throw (IllegalStateException.)))
  ([message] (throw (IllegalStateException. ^String message)))
  ([message cause] (throw (IllegalStateException. ^String message ^Throwable cause))))

(defn throw-io
  ([] (throw (IOException.)))
  ([message] (throw (IOException. ^String message)))
  ([message cause] (throw (IOException. ^String message ^Throwable cause))))
