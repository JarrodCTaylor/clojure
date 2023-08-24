;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Stuart Halloway, Daniel Solano Gómez

(ns clojure.test-clojure.vectors
  (:use clojure.test)
  (:import
    [java.util Collection Spliterator]
    [java.util.function Consumer]
    [java.util.stream Collectors]))

(deftest test-reversed-vec
  (let [r (range 6)
        v (into (vector-of :int) r)
        reversed (.rseq v)]
    (testing "returns the right impl"
      (is (= clojure.lang.APersistentVector$RSeq (class reversed))))
    (testing "RSeq methods"
      (is (= [5 4 3 2 1 0] reversed))
      (is (= 5 (.index reversed)))
      (is (= 5 (.first reversed)))
      (is (= [4 3 2 1 0] (.next reversed)))
      (is (= [3 2 1 0] (.. reversed next next)))
      (is (= 6 (.count reversed))))
    (testing "clojure calling through"
      (is (= 5 (first reversed)))
      (is (= 5 (nth reversed 0))))
    (testing "empty reverses to nil"
      (is (nil? (.. v empty rseq))))))

(deftest test-vecseq
  (let [r (range 100)
        vs (into (vector-of :int) r)
        vs-1 (next vs)
        vs-32 (.chunkedNext (seq vs))]
    (testing "="
      (are [a b] (= a b)
           vs vs
           vs-1 vs-1
           vs-32 vs-32)
      (are [a b] (not= a b)
           vs vs-1
           vs-1 vs
           vs vs-32
           vs-32 vs))
    (testing "IPersistentCollection.empty"
      (are [a] (identical? clojure.lang.PersistentList/EMPTY (.empty (seq a)))
           vs vs-1 vs-32))
    (testing "IPersistentCollection.cons"
      (are [result input] (= result (.cons input :foo))
           [:foo 1] (seq (into (vector-of :int) [1]))))
    (testing "IPersistentCollection.count"
      (are [ct s] (= ct (.count (seq s)))
           100 vs
           99 vs-1
           68 vs-32)
      ;; can't manufacture this scenario: ASeq defers to Counted, but
      ;; LazySeq doesn't, so Counted never gets checked on reified seq below
      #_(testing "hops to counted when available"
        (is (= 200
               (.count (concat
                        (seq vs)
                        (reify clojure.lang.ISeq
                               (seq [this] this)
                               clojure.lang.Counted
                               (count [_] 100))))))))
    (testing "IPersistentCollection.equiv"
      (are [a b] (true? (.equiv a b))
           vs vs
           vs-1 vs-1
           vs-32 vs-32
           vs r)
      (are [a b] (false? (.equiv a b))
           vs vs-1
           vs-1 vs
           vs vs-32
           vs-32 vs
           vs nil))
    (testing "internal-reduce"
      (is (= [99] (into [] (drop 99 vs)))))))

(deftest test-primitive-subvector-reduce
  ;; regression test for CLJ-1082
  (is (== 60 (let [prim-vec (into (vector-of :long) (range 1000))]
               (reduce + (subvec prim-vec 10 15))))))

(deftest test-vec-compare
  (let [nums      (range 1 100)
        ; randomly replaces a single item with the given value
        rand-replace  (fn[val]
                        (let [r (rand-int 99)]
                          (concat (take r nums) [val] (drop (inc r) nums))))
        ; all num sequences in map
        num-seqs      {:standard       nums
                       :empty          '()
                       ; different lengths
                       :longer         (concat nums [100])
                       :shorter        (drop-last nums)
                       ; greater by value
                       :first-greater  (concat [100] (next nums))
                       :last-greater   (concat (drop-last nums) [100])
                       :rand-greater-1 (rand-replace 100)
                       :rand-greater-2 (rand-replace 100)
                       :rand-greater-3 (rand-replace 100)
                       ; lesser by value
                       :first-lesser   (concat [0] (next nums))
                       :last-lesser    (concat (drop-last nums) [0])
                       :rand-lesser-1  (rand-replace 0)
                       :rand-lesser-2  (rand-replace 0)
                       :rand-lesser-3  (rand-replace 0)}
        ; a way to create compare values based on num-seqs
        create-vals   (fn[base-val]
                        (zipmap (keys num-seqs)
                                (map #(into base-val %1) (vals num-seqs))))
        ; Vecs made of int primitives
        int-vecs      (create-vals (vector-of :int))
        ; Vecs made of long primitives
        long-vecs     (create-vals (vector-of :long))
        ; standard boxing vectors
        regular-vecs  (create-vals [])
        ; the standard int Vec for comparisons
        int-vec       (:standard int-vecs)]
    (testing "compare"
      (testing "identical"
        (is (= 0 (compare int-vec int-vec))))
      (testing "equivalent"
        (are [x y] (= 0 (compare x y))
             ; standard
             int-vec (:standard long-vecs)
             (:standard long-vecs) int-vec
             int-vec (:standard regular-vecs)
             (:standard regular-vecs) int-vec
             ; empty
             (:empty int-vecs) (:empty long-vecs)
             (:empty long-vecs) (:empty int-vecs)))
      (testing "lesser"
        (are [x] (= -1 (compare int-vec x))
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs))
        (are [x] (= -1 (compare x int-vec))
             nil
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs)))
      (testing "greater"
        (are [x] (= 1 (compare int-vec x))
             nil
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs))
        (are [x] (= 1 (compare x int-vec))
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs))))
    (testing "Comparable.compareTo"
      (testing "incompatible"
        (is (thrown? NullPointerException (.compareTo int-vec nil)))
        (are [x] (thrown? ClassCastException (.compareTo int-vec x))
             '()
             {}
             #{}
             (sorted-set)
             (sorted-map)
             nums
             1))
      (testing "identical"
        (is (= 0 (.compareTo int-vec int-vec))))
      (testing "equivalent"
        (are [x] (= 0 (.compareTo int-vec x))
             (:standard long-vecs)
             (:standard regular-vecs)))
      (testing "lesser"
        (are [x] (= -1 (.compareTo int-vec x))
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs)))
      (testing "greater"
        (are [x] (= 1 (.compareTo int-vec x))
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs))))))

(deftest test-vec-associative
  (let [empty-v (vector-of :long)
        v       (into empty-v (range 1 6))]
    (testing "Associative.containsKey"
      (are [x] (.containsKey v x)
           0 1 2 3 4)
      (are [x] (not (.containsKey v x))
           -1 -100 nil [] "" #"" #{} 5 100)
      (are [x] (not (.containsKey empty-v x))
           0 1))
    (testing "contains?"
      (are [x] (contains? v x)
           0 2 4)
      (are [x] (not (contains? v x))
           -1 -100 nil "" 5 100)
      (are [x] (not (contains? empty-v x))
           0 1))
    (testing "Associative.entryAt"
      (are [idx val] (= (clojure.lang.MapEntry. idx val)
                        (.entryAt v idx))
           0 1
           2 3
           4 5)
      (are [idx] (nil? (.entryAt v idx))
           -5 -1 5 10 nil "")
      (are [idx] (nil? (.entryAt empty-v idx))
           0 1))))

(deftest test-vec-creation
  (testing "Plain (vector-of :type)"
    (are [x] (and (empty? x) (instance? clojure.core.Vec x))
         (vector-of :boolean)
         (vector-of :byte)
         (vector-of :short)
         (vector-of :int)
         (vector-of :long)
         (vector-of :float)
         (vector-of :double)
         (vector-of :char))
    (testing "with invalid type argument"
      (are [x] (thrown? IllegalArgumentException x)
           (vector-of nil)
           (vector-of Float/TYPE)
           (vector-of 'int)
           (vector-of :integer)
           (vector-of ""))))
  (testing "vector-like (vector-of :type x1 x2 x3 … xn)"
    (are [vec gvec] (and (instance? clojure.core.Vec gvec)
                         (= (into (vector-of :int) vec) gvec)
                         (= vec gvec)
                         (= (hash vec) (hash gvec)))
         [1] (vector-of :int 1)
         [1 2] (vector-of :int 1 2)
         [1 2 3] (vector-of :int 1 2 3)
         [1 2 3 4] (vector-of :int 1 2 3 4)
         [1 2 3 4 5] (vector-of :int 1 2 3 4 5)
         [1 2 3 4 5 6] (vector-of :int 1 2 3 4 5 6)
         (apply vector (range 1000)) (apply vector-of :int (range 1000))
         [1 2 3] (vector-of :int 1M 2.0 3.1)
         [97 98 99] (vector-of :int \a \b \c))
    (testing "with null values"
      (are [x] (thrown? NullPointerException x)
        (vector-of :int nil)
        (vector-of :int 1 nil)
        (vector-of :int 1 2 nil)
        (vector-of :int 1 2 3 nil)
        (vector-of :int 1 2 3 4 nil)
        (vector-of :int 1 2 3 4 5 nil)
        (vector-of :int 1 2 3 4 5 6 nil)))
    (testing "with unsupported values"
      (are [x] (thrown? ClassCastException x)
           (vector-of :int true)
           (vector-of :int 1 2 3 4 5 false)
           (vector-of :int {:a 1 :b 2})
           (vector-of :int [1 2 3 4] [5 6])
           (vector-of :int '(1 2 3 4))
           (vector-of :int #{1 2 3 4})
           (vector-of :int (sorted-set 1 2 3 4))
           (vector-of :int 1 2 "3")
           (vector-of :int "1" "2" "3")))
    (testing "instances of IPersistentVector"
      (are [gvec] (instance? clojure.lang.IPersistentVector gvec)
        (vector-of :int 1 2 3)
        (vector-of :double 1 2 3)))
    (testing "fully implements IPersistentVector"
      (are [gvec] (= 3 (.length gvec))
        (vector-of :int 1 2 3)
        (vector-of :double 1 2 3)))))

(deftest empty-vector-equality
  (let [colls [[] (vector-of :long) '()]]
    (doseq [c1 colls, c2 colls]
      (is (= c1 c2))
      (is (.equals c1 c2)))))

(defn =vec
  [expected v] (and (vector? v) (= expected v)))

(deftest test-mapv
  (are [r c1] (=vec r (mapv + c1))
       [1 2 3] [1 2 3])
  (are [r c1 c2] (=vec r (mapv + c1 c2))
       [2 3 4] [1 2 3] (repeat 1))
  (are [r c1 c2 c3] (=vec r (mapv + c1 c2 c3))
       [3 4 5] [1 2 3] (repeat 1) (repeat 1))
  (are [r c1 c2 c3 c4] (=vec r (mapv + c1 c2 c3 c4))
       [4 5 6] [1 2 3] [1 1 1] [1 1 1] [1 1 1]))

(deftest test-filterv
  (are [r c1] (=vec r (filterv even? c1))
       [] [1 3 5]
       [2 4] [1 2 3 4 5]))

(deftest test-subvec
  (let [v1 (vec (range 100))
        v2 (subvec v1 50 57)]
    (is (thrown? IndexOutOfBoundsException (v2 -1)))
    (is (thrown? IndexOutOfBoundsException (v2 7)))
    (is (= (v1 50) (v2 0)))
    (is (= (v1 56) (v2 6)))))

(deftest test-vec
  (is (= [1 2] (vec (first {1 2}))))
  (is (= [0 1 2 3] (vec [0 1 2 3])))
  (is (= [0 1 2 3] (vec (list 0 1 2 3))))
  (is (= [0 1 2 3] (vec (sorted-set 0 1 2 3))))
  (is (= [[1 2] [3 4]] (vec (sorted-map 1 2 3 4))))
  (is (= [0 1 2 3] (vec (range 4))))
  (is (= [\a \b \c \d] (vec "abcd")))
  (is (= [0 1 2 3] (vec (object-array (range 4)))))
  (is (= [1 2 3 4] (vec (eduction (map inc) (range 4)))))
  (is (= [0 1 2 3] (vec (reify clojure.lang.IReduceInit
                          (reduce [_ f start]
                            (reduce f start (range 4))))))))

(deftest test-reduce-kv-vectors
  (is (= 25 (reduce-kv + 10 [2 4 6])))
  (is (= 25 (reduce-kv + 10 (subvec [0 2 4 6] 1)))))

(deftest test-vector-eqv-to-non-counted-types
  (is (not= (range) [0 1 2]))
  (is (not= [0 1 2] (range)))
  (is (= [0 1 2] (take 3 (range))))
  (is (= [0 1 2] (new java.util.ArrayList [0 1 2])))
  (is (not= [1 2] (take 1 (cycle [1 2]))))
  (is (= [1 2 3 nil 4 5 6 nil] (eduction cat [[1 2 3 nil] [4 5 6 nil]]))))

(set! *warn-on-reflection* true)

;; remember returns a consumer that adds to an-atom of coll
(defn remember
  ^Consumer [an-atom]
  (reify Consumer (accept [_ v] (swap! an-atom conj v))))

(deftest test-empty-vector-spliterator
  (let [v []
        s (.spliterator ^Collection v)
        seen (atom [])]
    (is (= 0 (.estimateSize s) (.getExactSizeIfKnown s)))
    (is (nil? (.trySplit s)))
    (is (false? (.tryAdvance s (remember seen))))
    (is (= @seen []))))

;; tryAdvance then forEachRemaining walks vector spliterator
(deftest test-spliterator-tryadvance-then-forEach
  (let [n 66
        source-vec (vec (range n))]
    (for [v (into [(vec (range n))] (map #(subvec source-vec % (+ % 33)) (range 33)))]
      (dotimes [up-to n]
        (let [s (.spliterator ^Collection v)
              seen (atom [])
              consumer (remember seen)]
          (loop [i 0]
            (if (< i up-to)
              (do (is (true? (.tryAdvance s consumer))) (recur (inc i)))
              (.forEachRemaining s consumer)))
          (is (= v @seen))
          (is (false? (.tryAdvance s consumer))))))))

;; recursively split vector spliterators, walk all of the splits
(deftest test-spliterator-trySplit
  (dotimes [n 257]
    (let [v (vec (range n))
          seen (atom #{})
          consumer (remember seen)
          splits (loop [ss [(.spliterator ^Collection v)]]
                   (let [ss' (map #(.trySplit ^Spliterator %) ss)]
                     (if (every? nil? ss')
                       ss
                       (recur (into ss (remove nil? ss'))))))]
      (loop [[spl & sr] splits]
        (when spl
          (.forEachRemaining ^Spliterator spl consumer)
          (recur sr)))
      (is (= v (sort @seen))))))

(deftest test-vector-parallel-stream
  (dotimes [n 1024]
    (let [v (vec (range n))]
      (is (= n
            (-> ^Collection v .stream (.collect (Collectors/counting)))
            (-> ^Collection v .parallelStream (.collect (Collectors/counting)))
            (-> v ^Collection (subvec 0 n) .stream (.collect (Collectors/counting)))
            (-> v ^Collection (subvec 0 n) .parallelStream (.collect (Collectors/counting))))))))