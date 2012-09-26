(ns #^{:author "Jason"
       :doc "Simple clojure library for interacting with Akka actors"}
  akka-clojure.core
  (:import
   [akka.actor ActorRef ActorSystem Props UntypedActor
    UntypedActorFactory OneForOneStrategy SupervisorStrategy]
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

(defn- make-props [actor]
  (.withCreator
   (Props.)
   (proxy [UntypedActorFactory] []
     (create [] (actor)))))

(defmacro super-stateless [method fun & args]
  `(if (nil? ~fun)
     (proxy-super ~method ~@args)
     (~fun ~@args)))

(defn- proxy-stateless-actor
  [fun {:keys [initial-state
	       supervisor-strategy
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

(defmacro super-stateful [method function state & args]
  `(if (nil? ~function)
     (proxy-super ~method ~@args)
     (let [next-state# (~function ~state ~@args)]
       (reset! @~state next-state#))))

(defn- proxy-stateful-actor
  [fun {:keys [initial-state
	       supervisor-strategy
	       post-stop
	       pre-start
	       pre-restart
	       post-restart]}]
  (let [state (atom initial-state)]
    #(proxy [UntypedActor] []
       (postStop
	[]
	(super-stateful postStop post-stop state))
       (preStart
	[]
	(super-stateful preStart pre-start state))
       (preRestart
	[reason msg]
	(super-stateful preRestart pre-restart state reason msg))
       (postRestart
	[reason]
        (super-stateful postRestart post-restart state reason))
       (supervisorStrategy
	[]
	(if (nil? supervisor-strategy)
	  (proxy-super supervisorStrategy)
	  (let [{:keys [constructor function args]} supervisor-strategy
		mod-function (proxy [Function] []
			       (apply [t] 
				      (let [s state]
					(function t @s))))]
	    (apply constructor (concat args [mod-function])))))
       (onReceive
	[msg]
	(binding [self this
		  context (.getContext this)
		  sender (.getSender this)
		  parent (.. this (getContext) (parent))]
	  (let [next-state (fun msg @state)]
	    (reset! state next-state)))))))

(defn- make-actor [ctx fun actor-proxy {name :name :as map}] 
  (let [props (make-props (actor-proxy fun map))]
    (if (nil? name)
      (.actorOf ctx props)
      (.actorOf ctx props name))))

(defn actor-for [path]
  (.actorFor (if (nil? context) *actor-system* context) path))

(defn actor
  ([fun]
     (actor fun {}))
  ([fun {stateful :stateful
	 :as map}]
     (make-actor
      (if (nil? self)
	*actor-system*
	(.getContext self))
      fun
      (if stateful
	proxy-stateful-actor
	proxy-stateless-actor)
      map)))
