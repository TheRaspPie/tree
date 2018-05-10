(ns therasppie.tree.util.general)

;; modified run! from clojure.core
(defn run-kv! [proc coll]
  (reduce-kv #(proc %2 %3) nil coll)
  nil)

(defmacro for-in [[i from to] & body]
  `(let [to# (long ~to)]
     (loop [~i (long ~from)]
       (when (<= ~i to#)
         ~@body
         (recur (unchecked-inc ~i))))))
