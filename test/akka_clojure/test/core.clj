(ns #^{:author "Jason"
       :doc "Tests for akka clojure core"}
  akka-clojure.test.core
  (:use
   [akka-clojure.core]
   [clojure.test])
  (:import
   [java.lang Thread]))

(deftest ask-works
  (let [a (actor (fn [msg _]
		   (reply (if (= "foo" msg)
			    "bar"
			    "baz"))))
	val (wait (? a "foo" (millis 500)))]
    (is (= "bar" val))))

(deftest supervisor-invoked
  (let [proof (atom nil)
	child (fn [msg _]
		(throw (Exception. "woot")))
	supervisor (actor (fn [msg _]
			    (let [c (actor child)]
			      (! c msg)
			      (reply "ok")))
			  {:supervisor-strategy
			   (one-for-one
			    #(let [msg (.getMessage %)]
			       (reset! proof msg)
			       stop)) })
	val (wait (ask supervisor "hi" (millis 3000)))]
    (Thread/sleep 1000)
    (is (= "ok" val))
    (is (= "woot" @proof))))

(defn factorial [n]
  (loop [m n
	 acc 1]
    (if (= m 0)
      acc
      (recur (- m 1) (* m acc)))))


(defmulti supervisor :type)

(defmethod supervisor :result [res state]
	   (let [result (+ (:result state) (:value res))
		 replies (+ (:replies state) 1)]
	     (if (= replies (:n state))
	       (! (:sender state) result))
	     (assoc state
	       :replies replies
	       :result result)))

(defmethod supervisor :start [map state]
	   (let [n (:value map)
		 child (fn [n _]
			 (! parent
			    {:type :result,
			     :value (factorial n)}))]
	     (doseq [i (range 1 (+ n 1))]
		 (let [c (actor child)]
		   (! c i)))
	     {:sender sender,
	      :replies 0,
	      :result 0,
	      :n n}))

(deftest factorial-sum
  (let [sv (actor supervisor)
	res (wait (? sv {:type :start, :value 10} (millis 10000)))]
    (is (= 4037913 res))))

(deftest pre-start
  (let [proof (atom 0)
	a (actor (fn [msg _] (reply "hi"))
		 { :pre-start #(reset! proof 1) })
	val (wait (? a "hello" (millis 10000)))]
    (is (= "hi" val))
    (is (= 1 @proof))))
	