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
     (let [function (proxy [Function] []
		   (apply [t] (fun t)))]
       (proxy
	   [OneForOneStrategy]
	   [max-retries within-time-range function]))))
      

(defn- make-actor
  ([context fun]
     (make-actor context nil fun))
  ([context supervisor-strategy fun]
     (let [state (atom {})]
       (.actorOf
	context
	(.withCreator
	 (Props.)
	 (proxy [UntypedActorFactory] []
	   (create []
		   (proxy [UntypedActor] []
		     (supervisorStrategy
		      []
		      (if (nil? supervisor-strategy)
			(proxy-super supervisorStrategy)
			supervisor-strategy))
		     (onReceive
		      [msg]
		      (binding [self this
				context (.getContext this)
				sender (.getSender this)
				parent (.. this (getContext) (parent))]
			(let [next-state (fun msg @state)]
			  (reset! state next-state))))))))))))

(defn actor 
  "Create a new actor. If called in the context of another actor,
this function will create a parent-child relationship."
  ([fun]
     (actor fun nil))
  ([fun supervisor-strategy]
     (make-actor
      (if (nil? self) *actor-system* (.getContext self))
      supervisor-strategy
      fun)))
      
