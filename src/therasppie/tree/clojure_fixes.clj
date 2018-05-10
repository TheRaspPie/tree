(in-ns 'clojure.core)

;; optimized version of if-not
(alter-var-root #'if-not
  (constantly
    (fn
      ([_ _ test then] `(if-not ~test ~then nil))
      ([_ _ test then else]
       `(if ~test ~else ~then)))))

;; optimized version of str
(alter-var-root #'str
  (constantly
    (fn
      (^String [] "")
      (^String [^Object x]
       (if (nil? x) "" (. x (toString))))
      (^String [x & ys]
       (loop [^StringBuilder sb (new StringBuilder (str x))
              more ys]
         (if more
           (recur (. sb (append (str (first more)))) (next more))
           (str sb)))))))

(in-ns 'clojure.spec.alpha)

(alter-var-root #'keys
  (constantly
    (fn
      [& {:keys [req req-un opt opt-un gen only-specified?]}]
      (let [unk #(-> % name keyword)
            req-keys (filterv keyword? (flatten req))
            req-un-specs (filterv keyword? (flatten req-un))
            _ (c/assert (every? #(c/and (keyword? %) (namespace %)) (concat req-keys req-un-specs opt opt-un))
                        "all keys must be namespace-qualified keywords")
            req-specs (into req-keys req-un-specs)
            req-keys (into req-keys (map unk req-un-specs))
            opt-keys (into (vec opt) (map unk opt-un))
            opt-specs (into (vec opt) opt-un)
            gx (gensym)
            parse-req (fn [rk f]
                        (map (fn [x]
                               (if (keyword? x)
                                 `(contains? ~gx ~(f x))
                                 (walk/postwalk
                                   (fn [y] (if (keyword? y) `(contains? ~gx ~(f y)) y))
                                   x)))
                          rk))
            pred-exprs [`(map? ~gx)]
            pred-exprs (into pred-exprs (parse-req req identity))
            pred-exprs (into pred-exprs (parse-req req-un unk))
            keys-pred `(fn* [~gx] (c/and ~@pred-exprs))
            pred-exprs (mapv (fn [e] `(fn* [~gx] ~e)) pred-exprs)
            pred-forms (walk/postwalk res pred-exprs)]
        ;; `(map-spec-impl ~req-keys '~req ~opt '~pred-forms ~pred-exprs ~gen)
        `(map-spec-impl {:req '~req :opt '~opt :req-un '~req-un :opt-un '~opt-un
                         :req-keys '~req-keys :req-specs '~req-specs
                         :opt-keys '~opt-keys :opt-specs '~opt-specs
                         :pred-forms '~pred-forms
                         :pred-exprs ~pred-exprs
                         :keys-pred ~keys-pred
                         :gfn ~gen
                         :only-specified? ~only-specified?})))))

(alter-var-root #'map-spec-impl
  (constantly
    (fn
      [{:keys [req-un opt-un keys-pred pred-exprs opt-keys req-specs req req-keys opt-specs pred-forms opt gfn only-specified?]
        :as argm}]
      (let [k->s (zipmap (concat req-keys opt-keys) (concat req-specs opt-specs))
            keys->specnames (if only-specified? k->s #(c/or (k->s %) %))
            id (java.util.UUID/randomUUID)]
        (reify
          Specize
          (specize* [s] s)
          (specize* [s _] s)

          Spec
          (conform* [_ m]
            (if (keys-pred m)
              (let [reg (registry)]
                (loop [ret m, [[k v] & ks :as keys] m]
                  (if keys
                    (let [sname (keys->specnames k)]
                      (if-let [s (get reg sname)]
                        (let [cv (conform s v)]
                          (if (invalid? cv)
                            ::invalid
                            (recur (if (identical? cv v) ret (assoc ret k cv))
                                   ks)))
                        (recur ret ks)))
                    ret)))
              ::invalid))
          (unform* [_ m]
            (let [reg (registry)]
              (loop [ret m, [k & ks :as keys] (c/keys m)]
                (if keys
                  (if (contains? reg (keys->specnames k))
                    (let [cv (get m k)
                          v (unform (keys->specnames k) cv)]
                      (recur (if (identical? cv v) ret (assoc ret k v))
                             ks))
                    (recur ret ks))
                  ret))))
          (explain* [_ path via in x]
            (if-not (map? x)
              [{:path path :pred 'map? :val x :via via :in in}]
              (let [reg (registry)]
                (apply concat
                       (when-let [probs (->> (map (fn [pred form] (when-not (pred x) (abbrev form)))
                                               pred-exprs pred-forms)
                                             (keep identity)
                                             seq)]
                         (map
                           #(identity {:path path :pred % :val x :via via :in in})
                           probs))
                       (map (fn [[k v]]
                              (when-not (c/or (not (contains? reg (keys->specnames k)))
                                              (pvalid? (keys->specnames k) v k))
                                (explain-1 (keys->specnames k) (keys->specnames k) (conj path k) via (conj in k) v)))
                         (seq x))))))
          (gen* [_ overrides path rmap]
            (if gfn
              (gfn)
              (let [rmap (inck rmap id)
                    gen (fn [k s] (gensub s overrides (conj path k) rmap k))
                    ogen (fn [k s]
                           (when-not (recur-limit? rmap id path k)
                             [k (gen/delay (gensub s overrides (conj path k) rmap k))]))
                    req-gens (map gen req-keys req-specs)
                    opt-gens (remove nil? (map ogen opt-keys opt-specs))]
                (when (every? identity (concat req-gens opt-gens))
                  (let [reqs (zipmap req-keys req-gens)
                        opts (into {} opt-gens)]
                    (gen/bind (gen/choose 0 (count opts))
                              #(let [args (concat (seq reqs) (when (seq opts) (shuffle (seq opts))))]
                                 (->> args
                                      (take (c/+ % (count reqs)))
                                      (apply concat)
                                      (apply gen/hash-map)))))))))
          (with-gen* [_ gfn] (map-spec-impl (assoc argm :gfn gfn)))
          (describe* [_] (cons `keys
                               (cond-> []
                                       req (conj :req req)
                                       opt (conj :opt opt)
                                       req-un (conj :req-un req-un)
                                       opt-un (conj :opt-un opt-un)))))))))
