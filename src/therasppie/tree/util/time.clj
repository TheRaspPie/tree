(ns therasppie.tree.util.time
  (:import
    (java.util.concurrent TimeUnit)))

(defn convert [from to value]
  (.convert ^TimeUnit to value ^TimeUnit from))

(def seconds TimeUnit/SECONDS)
(def millis TimeUnit/MILLISECONDS)
(def micros TimeUnit/MICROSECONDS)
(def nanos TimeUnit/NANOSECONDS)
