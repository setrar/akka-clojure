(ns #^{:author "Jason"
       :doc "Simple clojure library for interacting with Akka actors"}
  akka-clojure.core
  (:import
   [akka.actor ActorRef ActorSystem Props UntypedActor UntypedActorFactory]
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

(defn reply [msg]
  (if (nil? self)
    (throw (RuntimeException. "Reply can only be used in the context of an actor"))
    (let [sender (.getSender self)]
      (.tell sender msg))))

(defn actor [fun]
  (.actorOf *actor-system*
   (.withCreator
    (Props.)
    (proxy [UntypedActorFactory] []
      (create
       [] (proxy [UntypedActor] []
	    (onReceive [msg]
		       (binding [self this]
			 (fun msg)))))))))
