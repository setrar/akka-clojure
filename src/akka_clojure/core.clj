(ns #^{:author "Jason"
       :doc "Simple clojure library for interacting with Akka actors"}
  akka-clojure.core
  (:import
   [akka.actor ActorRef ActorSystem Props UntypedActor
    UntypedActorFactory OneForOneStrategy SupervisorStrategy
    PoisonPill]
   [akka.routing RoundRobinRouter]
   [akka.japi Function]
   [akka.pattern Patterns]
   [akka.dispatch Await]
   [akka.util Duration]
   [java.util.concurrent TimeUnit]))

(def ^:dynamic *actor-system*
     (ActorSystem/create "default"))

(def ^:dynamic self nil)
(def ^:dynamic context nil)
(def ^:dynamic parent nil)
(def ^:dynamic sender nil)

(defstruct duration :unit :value)
(defstruct strategy :type :function :args)

(def escalate (SupervisorStrategy/escalate))
(def stop (SupervisorStrategy/stop))
(def resume (SupervisorStrategy/resume))
(def restart (SupervisorStrategy/restart))


(defn to-millis [duration]
  (.convert TimeUnit/MILLISECONDS
	    (:value duration)
	    (:unit duration)))

(defn millis [val]
  (struct-map duration
    :unit TimeUnit/MILLISECONDS
    :value val))

(defn ask
  ([^ActorRef actor msg timeout]
     (Patterns/ask actor msg (to-millis timeout))))

(def ? ask)

(defn wait
  ([future]
     (Await/result future (Duration/Inf)))
  ([future duration]
     (Await/result future (Duration/create
			   (:value duration)
			   (:unit duration)))))

(defn tell
  "Send a message to an actor."
  [actor msg]
  (.tell actor msg))

(def ! tell)

(defn reply
  "Reply to the sender of a message. Can ONLY be used from within an actor."
  [msg]
  (if (nil? self)
    (throw (RuntimeException. "Reply can only be used in the context of an actor"))
    (! sender msg)))

(defn one-for-one
  ([fun] (one-for-one fun -1 (Duration/Inf)))
  ([fun max-retries within-time-range]
     (struct-map strategy
       :constructor (fn [max-retries within-time-range function]
		      (OneForOneStrategy. max-retries within-time-range function))
       :function fun
       :args [max-retries within-time-range])))


(defn- untyped-factory [actor]
  (proxy [UntypedActorFactory] []
	    (create [] (actor))))

(defn- make-props
  ([actor router]
     (if (nil? router)
       (make-props actor)
       (.. (Props.)
	   (withRouter router)
	   (withCreator (untyped-factory actor)))))
  ([actor]
     (.withCreator
      (Props.)
      (untyped-factory actor))))

(defmacro super-stateless [method fun & args]
  `(if (nil? ~fun)
     (proxy-super ~method ~@args)
     (~fun ~@args)))

(defn- proxy-stateless-actor
  [fun {:keys [supervisor-strategy
	       post-stop
	       pre-start
	       pre-restart
	       post-restart]}]
  #(proxy [UntypedActor] []
     (postStop
      []
      (super-stateless postStop post-stop))
     (preStart
      []
      (super-stateless preStart pre-start))
     (preRestart
      [reason msg]
      (super-stateless preRestart pre-restart reason msg))
     (postRestart
      [reason]
      (super-stateless postRestart post-restart reason))
     (supervisorStrategy
      []
      (if (nil? supervisor-strategy)
	(proxy-super supervisorStrategy)
	(let [{:keys [constructor function args]} supervisor-strategy
	      mod-function (proxy [Function] []
			     (apply [t] (function t)))]
	  (apply constructor (concat args [mod-function])))))
     (onReceive
      [msg]
      (binding [self this
		context (.getContext this)
		sender (.getSender this)
		parent (.. this (getContext) (parent))]
	(fun msg)))))

(defn- make-actor [ctx fun props {name :name :as map}]
  (if (nil? name)
    (.actorOf ctx props)
    (.actorOf ctx props name)))

(defn actor-for [path]
  (.actorFor (if (nil? context) *actor-system* context) path))

(defn poison [a]
  (! a (PoisonPill/getInstance)))

(defn actor
  "Create an actor which invokes the passed function when a
message is received."
  ([fun]
     (actor fun {}))
  ([fun {router :router
	 :as map}]
     (make-actor
      (if (nil? self)
	*actor-system*
	(.getContext self))
      fun
      (make-props
       (proxy-stateless-actor fun map)
       router)
      map)))

(defmacro defactor [name args & body]
  (if (not (= (count args) 1))
    (throw (IllegalArgumentException. "Expected definition with one argument."))
    `(def ~name (actor (fn ~args ~@body)))))

(defmacro with-state [[sym initial-state] fun]
  `(let [st# (atom ~initial-state)]
     (fn [msg#]
       (let [~sym @st#
	     next-state# (~fun msg#)]
	 (reset! st# next-state#)))))

(defmacro round-robin [[n] fun]
  `(actor ~fun
	  {:router (RoundRobinRouter. ~n)}))
