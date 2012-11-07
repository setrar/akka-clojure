(ns #^{:author "Jason"
       :doc "Simple clojure library for interacting with Akka actors"}
  akka-clojure.core
  (:import
   [akka.actor ActorRef ActorSystem Props UntypedActor
    UntypedActorFactory OneForOneStrategy SupervisorStrategy
    PoisonPill]
   [akka.routing RoundRobinRouter RandomRouter BroadcastRouter
    SmallestMailboxRouter]
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


(defmulti #^{:private true} set-property
  (fn [props [k v]] k))

(defmethod set-property :router [props [_ router]]
	   (.withRouter props router))

(defmethod set-property :dispatcher [props [_ dispatcher]]
	   (.withDispatcher props dispatcher))

(defmethod set-property :actor [props [_ actor]]
	   (.withCreator props (untyped-factory actor)))

(defn props [map]
  (reduce set-property (Props.) map))

(defmacro proxy-if-nil [method fun & args]
  `(if (nil? ~fun)
     (proxy-super ~method ~@args)
     (~fun ~@args)))

(defn- untyped-actor
  [fun {:keys [supervisor-strategy
	       post-stop
	       pre-start
	       pre-restart
	       post-restart]}]
  #(let [f (if (map? fun)
	     (eval ((:fun fun)))
	     fun)]
     (proxy [UntypedActor] []
       (postStop
	[]
	(proxy-if-nil postStop post-stop))
       (preStart
	[]
	(proxy-if-nil preStart pre-start))
       (preRestart
	[reason msg]
	(proxy-if-nil preRestart pre-restart reason msg))
       (postRestart
	[reason]
	(proxy-if-nil postRestart post-restart reason))
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
	  (f msg))))))

(defn actor-for [path]
  (.actorFor (if (nil? context) *actor-system* context) path))

(defn poison [a]
  "Shortcut for killing a actor through Akka's poison pill."
  (! a (PoisonPill/getInstance)))

(defn actor
  "Create an actor which invokes the passed function when a
message is received."
  ([fun]
     (actor fun {}))
  ([fun map]
     (let [{name :name :as metadata} map
	   props (props (assoc (select-keys metadata
					    [:router :dispatcher])
			  :actor (untyped-actor fun metadata)))
	   ctx (if (nil? self)
		  *actor-system*
		  (.getContext self))]
       (if (nil? name)
	 (.actorOf ctx props)
	 (.actorOf ctx props name)))))

(defmacro defactor [name properties [msg] & body]
  `(def ~name (actor (fn [~msg] ~@body) ~properties)))

(defmacro with-state [[sym initial-state] fun]
  {:fun
   `(fn []
      (let [st# (atom ~initial-state)]
	(fn [msg#]
	  (let [~sym @st#
		next-state# (~fun msg#)]
	    (reset! st# next-state#)))))})
  
(defmacro state-machine [init-clause & when-clauses]
  (let [init-state (second init-clause)
	action (gensym "action")]
    `(actor (with-state [st# ~(second init-clause)]
	      (fn [~action]
		(case st#
		      ~@(apply concat
			       (for [when-clause when-clauses]
				 (let [[_ state [arg] body] when-clause]
				   `(~state (let [~arg ~action]
					      ~body)))))))))))
