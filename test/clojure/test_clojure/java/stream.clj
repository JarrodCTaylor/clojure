;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test-clojure.java.stream
  (:use clojure.test)
  (:import [java.util.stream Stream]
           [java.util.function Supplier]))

(deftest stream-seq!-test
  (let [none (.stream [])
        one  (Stream/of "a")
        n    (.stream ["a" "b" "c"])
	inf  (Stream/generate (reify Supplier (get [_] 42)))
        st   (stream-seq! one)]
    (is (empty? (map identity (stream-seq! none))))
    (is (seq? st))
    (is (= ["a"] (map identity st)))
    (is (= ["a" "b" "c"] (map identity (stream-seq! n))))
    (is (= [42 42 42 42 42] (take 5 (stream-seq! inf))))))

(deftest stream-into!-test
  (let [none (.stream [])
        one  (Stream/of "a")
        n    (.stream ["a" "b" "c"])
        inf  (Stream/generate (reify Supplier (get [_] 42)))]
    (is (empty? (stream-into! [] none)))
    (is (= ["a"] (stream-into! [] one)))
    (is (= ["a" "b" "c"] (stream-into! [] n)))
    (is (= [42 42 42 42 42]
           (stream-into! [] (.limit inf 5))))))
