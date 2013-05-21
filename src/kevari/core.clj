(ns kevari.core
  "A key value store; kevari = ke-va-ri = key-value-ring.")

; ---------------------
; Key Hashing (Private)
; ---------------------

(defn to-node-index
  "Converts an index to a node index."
  [ring i]
  (mod i (:n ring)))

(defn base-node-index-for-key
  "Returns the base node index for a key. The base node is not a primary node.
  (There are no primary nodes; all nodes are equal.)"
  [ring k]
  (to-node-index ring (hash k)))

(defn node-indices-for-key
  "Returns a vector of node indices for a key."
  [ring k]
  (let [base (base-node-index-for-key ring k)
        indices (range base (+ base (:w ring)))]
    (vec (map #(to-node-index ring %) indices))))

(defn nodes-for-key
  "Returns a vector of nodes responsible for storing a key."
  [ring k]
  (vec (map #((:nodes ring) %) (node-indices-for-key ring k))))

(defn rand-node-for-key
  "Returns a random node responsible for storing a key."
  [ring k]
  (rand-nth (nodes-for-key ring k)))

(defn node-index-for-key?
  "Is node index responsible for storing a key?"
  [ring i k]
  (boolean (some #{i} (node-indices-for-key ring k))))

(defn node-for-key?
  "Is node responsible for storing a key?"
  [ring node k]
  (boolean (some #{node} (nodes-for-key ring k))))

; ------------------------------
; Node State Functions (Private)
; ------------------------------

(defn put-store
  "Put key and value into store. Returns updated state."
  [state k v]
  (assoc-in state (vector :store k) v))

(defn concat-queue
  "Concatenate node-ids into replica queue. Returns updated state."
  [state node-ids k v]
  (let [queue (map #(conj [%] k v) node-ids)]
    (update-in state [:queue] (comp vec concat) queue)))

(defn concat-queue-for-hit
  "Put remaining node-ids into replica queue. Returns updated state.
  (For use when a node *is* a storage place for a key.)"
  [state ring k v]
  (let [id (:id state)
        all-ids (node-indices-for-key ring k)
        remaining-ids (remove #(= % id) all-ids)]
    (concat-queue state remaining-ids k v)))

(defn concat-queue-for-miss
  "Put all node-ids for key into replica queue. Returns updated state.
   (For use when a node is *not* a storage place for a key.)"
  [state ring k v]
  (let [all-ids (node-indices-for-key ring k)]
    (concat-queue state all-ids k v)))

(defn update-node-state-for-hit
  "Put key and value into store. Update replica queue. Returns updated state."
  [state ring k v]
  (-> state
      (put-store k v)
      (concat-queue-for-hit ring k v)))

(defn update-node-state-for-miss
  "Put key and value into store. Update replica queue. Returns updated state."
  [state ring k v]
  (concat-queue-for-miss state ring k v))

(defn update-node-state
  "Returns updated node state."
  [state ring k v]
  (if (node-index-for-key? ring (:id state) k)
    (update-node-state-for-hit state ring k v)
    (update-node-state-for-miss state ring k v)))

; ------------------------
; Node Functions (Private)
; ------------------------

(defn get-node-directly
  [node k]
  (get-in @node (vector :store k)))

; ---------------------
; Replication (Private)
; ---------------------

(defn queue-rest
  "Removes first item from replica queue. Returns updated state."
  [state]
  (update-in state [:queue] (comp vec rest)))

(defn node-dequeue
  "Remove the first item from the node replica queue."
  [node]
  (send node queue-rest))

(defn node-put-without-replication
  "Put key and value into the node. Does not update replication queue. For
  node-to-node messaging only."
  [node k v]
  (send node put-store k v))

(defn process-queue-item
  "Process one item from the replica queue. Note: internally, this function
  calls `node-dequeue` which causes watch functions to retrigger."
  [ring node item]
  (let [[id k v] item
        replica-node ((:nodes ring) id)]
    (node-dequeue node)
    (node-put-without-replication replica-node k v)))

(defn add-replica-watch
  "Adds a watch function to node that will process the entire queue. Note:
  each call to the watch function processes only the first queue item, but
  since `process-queue-item` causes the watch to retrigger."
  [ring node id]
  (let [process-queue
        (fn [watch-key node old-state state]
          (let [queue (:queue state)
                item (first queue)]
            (if item
              (process-queue-item ring node item))))]
    (add-watch node id process-queue)))

(defn add-replica-watches
  "Add replica watches to nodes in ring."
  [ring]
  (let [nodes (:nodes ring)]
    (dotimes [i (:n ring)]
      (add-replica-watch ring (nodes i) i))))

; ---------------------------
; Node Functions (Public API)
; ---------------------------

(defn get-node
  "Return the value for a given key from the ring by communicating with a
  particular node."
  [ring node k]
  (let [node' (if (node-for-key? ring node k)
                node
                (rand-node-for-key ring k))]
    (get-node-directly node' k)))

(defn put-node
  "Put key and value into the ring by communicating with a particular node."
  [ring node k v]
  (send node update-node-state ring k v))

; ---------------------------
; Ring Functions (Public API)
; ---------------------------

(defn get-ring
  "Return the value for a given key from the ring by communicating with a
  random node."
  [ring k]
  (get-node ring (rand-node-for-key ring k) k))

(defn put-ring
  "Put key and value into the ring by communicating with a random node."
  [ring k v]
  (put-node ring (rand-node-for-key ring k) k v))

; ------------------------
; Initialization (Private)
; ------------------------

(defn init-node
  "Return a node data structure."
  [id]
  (agent {:id id :store {} :queue []}))

(defn init-nodes
  "Returns a vector of n nodes."
  [n]
  (vec (map init-node (range 0 n))))

(defn init-ring
  "Return a ring data structure of size n with w write copies. Both n and w
  must be greater than zero, and n must be greater than w."
  [n w]
  (cond
    (<= n 0) (throw (Exception. "n must be 1 or greater"))
    (<= w 0) (throw (Exception. "w must be 1 or greater"))
    (> w n) (throw (Exception. "n must be greater than w"))
    :else {:n n :w w :nodes (init-nodes n)}))

; ---------------------------------
; Initialization and Other (Public)
; ---------------------------------

(defn make-ring
  "Returns a ring of size n with w write copies, ready for use."
  [n w]
  (let [ring (init-ring n w)]
    (add-replica-watches ring)
    ring))

(defn await-ring
  "Blocks the current thread (indefinitely!) until all actions dispatched
  thus far, from this thread or agent, to the agents in ring have occurred.
  Will block on failed agents. Will never return if a failed agent is
  restarted with :clear-actions true."
  [ring]
  (let [nodes (:nodes ring)]
    (apply await nodes)))

(defn await-ring-for
  "Blocks the current thread until all actions dispatched thus far (from this
  thread or agent) to the agents in ring have occurred, or the timeout (in
  milliseconds) has elapsed. Returns logical false if returning due to
  timeout, logical true otherwise."
  [ring timeout-ms]
  (let [nodes (:nodes ring)]
    (apply await-for timeout-ms nodes)))

; ---------
; Profiling
; ---------

(defmacro time-ms
  "Evaluates expr. Returns elapsed milliseconds."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         end# (. System (nanoTime))
         elapsed# (/ (double (- end# start#)) 1000000.0)]
     elapsed#))

(defn load-ring
  "Load x key-value pairs into ring."
  [ring x]
  (dotimes [i x]
    (let [k (keyword (str "k" i))
          v (keyword (str "v" i))]
      (put-ring ring k v))))

(defn time-load-ring
  "Returns the time elapsed to load x key-value pairs into a (n, w) ring."
  [n w x]
  (let [ring (init-ring n w)]
    (time-ms
      (do
        (load-ring ring x)
        (await-ring ring)))))

(defn profile-load-ring
  "Returns a seq of trials; each trial is the amount of time required to load
  k key-value pairs into a (n, w) ring."
  [warm-up trials n w x]
  (dotimes [_ warm-up] (time-load-ring n w x))
  (repeatedly trials #(time-load-ring n w x)))

(defn average
  "Returns average of collection."
  [coll]
  (/ (reduce + coll) (count coll)))

(defn avg-time-load-ring
  "Returns average time to load a (n,w) ring with x key-value pairs."
  [warm-up trials n w x]
  (average (profile-load-ring warm-up trials n w x)))

(defn avg-time-scale-n
  "Returns average time from scaling n by z."
  [warm-up trials n w x z]
  (avg-time-load-ring warm-up trials (* n z) w x))

(defn avg-time-scale-w
  "Returns average time from scaling w by z."
  [warm-up trials n w x z]
  (avg-time-load-ring warm-up trials n (* w z) x))

(defn avg-time-scale-x
  "Returns average time from scaling x by z."
  [warm-up trials n w x z]
  (avg-time-load-ring warm-up trials n w (* x z)))

(defn print-n-scaling
  [warm-up trials n w x zs]
  (let [base (avg-time-load-ring warm-up trials n w x)]
    (println (format "n=%d w=%d x=%d" n w x))
    (println (format "baseline=%.1f ms" base))
    (doseq [z zs]
      (let [t (avg-time-scale-n 0 trials n w x z)]
        (println (format "%5d * x : %7.1f * baseline" z (/ t base)))))))

(defn print-w-scaling
  [warm-up trials n w x zs]
  (let [base (avg-time-load-ring warm-up trials n w x)]
    (println (format "n=%d w=%d x=%d" n w x))
    (println (format "baseline=%.1f ms" base))
    (doseq [z zs]
      (let [t (avg-time-scale-w 0 trials n w x z)]
        (println (format "%5d * x : %7.1f * baseline" z (/ t base)))))))

(defn print-x-scaling
  [warm-up trials n w x zs]
  (let [base (avg-time-load-ring warm-up trials n w x)]
    (println (format "n=%d w=%d x=%d" n w x))
    (println (format "baseline=%.1f ms" base))
    (doseq [z zs]
      (let [t (avg-time-scale-x 0 trials n w x z)]
        (println (format "%5d * x : %7.1f * baseline" z (/ t base)))))))
