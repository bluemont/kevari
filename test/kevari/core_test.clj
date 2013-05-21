(ns kevari.core-test
  (:require [clojure.test :refer :all]
            [kevari.core :refer :all]))

(deftest test-make-ring
  (testing "make-ring"
    (let [ring (make-ring 10 3)]
      (is (= 10 (:n ring)))
      (is (= 3 (:w ring))))))

(deftest test-to-node-index
  (testing "to-node-index"
    (let [ring (make-ring 3 1)]
      (is (= 0 (to-node-index ring 0)))
      (is (= 1 (to-node-index ring 1)))
      (is (= 2 (to-node-index ring 2)))
      (is (= 0 (to-node-index ring 3)))
      (is (= 1 (to-node-index ring 4)))
      (is (= 2 (to-node-index ring 5))))))

(deftest test-node-indices-for-key
  (testing "node-indices-for-key"
    (let [ring (make-ring 6 3)]
      (is (= [3 4 5] (node-indices-for-key ring :dupont)))
      (is (= [4 5 0] (node-indices-for-key ring :canvas))))))

(deftest test-nodes-for-key
  (testing "nodes-for-key"
    (let [ring (make-ring 6 1)
          nodes (:nodes ring)]
      (is (= [(nodes 3)] (nodes-for-key ring :dupont)))
      (is (= [(nodes 4)] (nodes-for-key ring :canvas))))))

(deftest test-node-for-key?
  (testing "node-for-key?"
    (let [ring (make-ring 6 3)
          nodes (:nodes ring)]
      (is (= false (node-for-key? ring (nodes 0) :dupont)))
      (is (= false (node-for-key? ring (nodes 1) :dupont)))
      (is (= false (node-for-key? ring (nodes 2) :dupont)))
      (is (= true  (node-for-key? ring (nodes 3) :dupont)))
      (is (= true  (node-for-key? ring (nodes 4) :dupont)))
      (is (= true  (node-for-key? ring (nodes 5) :dupont))))))

(deftest test-put-store
  (testing "put-store"
    (is (= {:store {:dog :preston}}
           (put-store {} :dog :preston)))))

(deftest test-concat-queue
  (testing "concat-queue"
    (is (= {:queue [[1 :x :y]]}
           (concat-queue {} [1] :x :y)))
    (is (= {:queue [[2 :x :y] [3 :x :y]]}
           (concat-queue {} [2 3] :x :y)))))

(deftest test-concat-queue-for-hit
  (testing "concat-queue-for-hit"
    (let [ring (make-ring 6 3)]
      (is (= {:queue [[4 :x :y] [5 :x :y]] :id 0}
             (concat-queue-for-hit {:id 0} ring :x :y)))
      (is (= {:queue [[4 :x :y] [5 :x :y] [0 :x :y]] :id 1}
             (concat-queue-for-hit {:id 1} ring :x :y))))))

(deftest test-concat-queue-for-miss
  (testing "concat-queue-for-miss"
    (let [ring (make-ring 6 3)]
      (is (= {:queue [[4 :x :y] [5 :x :y] [0 :x :y]] :id 0}
             (concat-queue-for-miss {:id 0} ring :x :y)))
      (is (= {:queue [[4 :x :y] [5 :x :y] [0 :x :y]] :id 1}
             (concat-queue-for-miss {:id 1} ring :x :y))))))

(deftest test-get-ring
  (testing "get-ring"
    (let [ring (make-ring 6 3)]
      (is (= nil (get-ring ring :a))))))

(deftest test-ring-set-and-get
  (testing "ring-set then ring-get"
    (let [ring (make-ring 6 3)]
      (put-ring ring :dog :preston)
      (await-ring-for ring 50)
      (is (= :preston (get-ring ring :dog))))
    (let [ring (make-ring 6 3)]
      (put-ring ring :k1 :v1)
      (put-ring ring :k2 :v2)
      (put-ring ring :k3 :v3)
      (await-ring-for ring 50)
      (is (= :v1 (get-ring ring :k1)))
      (is (= :v2 (get-ring ring :k2)))
      (is (= :v3 (get-ring ring :k3))))))

(deftest test-time-load-ring
  (testing "time-load-ring"
    (is (number? (time-load-ring 10 3 1000)))))

(deftest test-profile-load-ring
  (testing "profile-load-ring"
    (let [result (profile-load-ring 2 5 10 3 1000)]
      (is (seq? result))
      (is (= 5 (count result))))))
