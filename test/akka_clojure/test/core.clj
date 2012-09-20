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

(deftest supervisor-works
  (let [child #(throw (Exception. %1))
	supervisor (actor #(let [c (actor child)]
			     (! c %1)
			     (reply "ok"))
			  (one-for-one
			   #(do (println (.getMessage %1)) stop)))
	val (wait (ask supervisor "hi" (millis 500)))]
    (is (= "ok" val))))


