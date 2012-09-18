(ns #^{:author "Jason"
       :doc "Tests for akka clojure core"}
  akka-clojure.test.core
  (:use
   [akka-clojure.core]
   [clojure.test]))

(deftest ask-works
  (let [a (actor #(reply (if (= "foo" %1)
			   "bar"
			   "baz")))
	val (wait (ask a "foo" (millis 500)))]
    (is (= "bar" val))))

