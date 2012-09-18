(ns #^{:author "Jason"
       :doc "Simple clojure library for interacting with Akka actors"}
  akka-clojure.core
  (:import
   [akka.actor ActorRef ActorSystem Props UntypedActor UntypedActorFactory OneForOneStrategy]
   [akka.japi Function]
   [akka.pattern Patterns]
   [akka.dispatch Await]
   [akka.util Duration]
   [java.util.concurrent TimeUnit]))

(def ^:dynamic *actor-system*
     (ActorSystem/create "default"))

(def ^:dynamic self nil)

(defstruct duration :unit :value)

(defn to-millis [duration]
  (.convert TimeUnit/MILLISECONDS (:value duration) (:unit duration)))

(defn millis [val]
  (struct-map duration
    :unit TimeUnit/MILLISECONDS
    :value val))

(defn ask
  ([^ActorRef actor msg timeout]
     (Patterns/ask actor msg (to-millis timeout))))

(defn wait [future]
  (Await/result future (Duration/create 5 TimeUnit/SECONDS)))

(defn reply
  "Reply to the sender of a message. Can ONLY be used from within an actor."
  [msg]
  (if (nil? self)
    (throw (RuntimeException. "Reply can only be used in the context of an actor"))
    (let [sender (.getSender self)]
      (.tell sender msg))))

(defn one-for-one [max-retries fun]
  (let [function (proxy [Function] []
		   (apply [t] (fun t)))]
    (proxy [OneForOneStrategy] [max-retries (Duration/Inf) function])))
      
(defn !
  "Send a message to the actor."
  [actor msg]
  (.tell actor msg))
		     
(defn- make-actor [context fun]
  (.actorOf context
	    (.withCreator
	     (Props.)
	     (proxy [UntypedActorFactory] []
	       (create
		[] (proxy [UntypedActor] []
		     (onReceive [msg]
				(binding [self this]
				  (fun msg)))))))))

(defn actor 
  "Create a new actor. If called in the context of another actor,
this function will create a parent-child relationship."
  [fun]
  (make-actor (if (nil? self) *actor-system* self) fun))
      
