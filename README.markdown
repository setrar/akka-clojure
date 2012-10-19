
akka-clojure
============

Akka-clojure is a simple Clojure wrapper for Akka.

Usage
-----

Actors are created with the *actor* function in akka-clojure.core, which
takes a callback function with a single parameter, which is the
received message. The example below shows the basic usage.

```clojure
(use '(akka-clojure core))

(let [a (actor #(println "Received " %))]
     (! a "hello"))
```

In some cases, it is desirable to carry state between invocations of
the actor's receive callback.  For these cases, a *with-state* macro
is provided. Whenever a message is received, the result of the 
callback will become the state on the next invocation. For example:

```clojure
(let [a (actor
          (with-state [count 0]
	    (fn [msg]
              (println count)
     	      (inc count))))]
  (! a "hi")
  (! a "hi"))    
```

When run, this will print "0" and "1" to the console.

You can also create an actor from the context of another actor. In 
this case, the parent actor supervises its child and will be notified
when it fails. 

```clojure
(defn supervisor [msg]
      (let [child (actor (fn [msg] (println msg)))]
      	   (! child msg)))

(let [s (actor supervisor)]
     (! s "hello"))
```

To receive failure notifications, you should provide a supervisor strategy
callback. In the example below, when a child fails, the exception is passed
to the callback, which instructs the child to stop on a one for one basis. 

```clojure
(actor supervisor { :supervisor-strategy (one-for-one #(do (println %) stop)) })
```

The four actions that may be taken on child failure are resume, restart,
escalate, and stop.

As you may have guessed the +!+ function corresponds to Akka's *tell*,
which can also be used. Additionally, for synchronous interaction, you
can use Akka's 'ask' pattern, which is available through *?* or *ask*.

```clojure
(let [a (actor (fn [msg] (reply "hi")))]
     (println (wait (? a "hello" (millis 500)))))
```

When this is run, the message "hi" will be printed to the console.
The third parameter to *?* is the timeout. The reply function is used
to send a message back to the sender.

Akka-clojure exposes four dynamic variables to an actor: *self*, *context*,
*sender* and *parent*. This gives you direct access to the Akka API.
For example:

```clojure
(actor #(.tell sender %))
```

Routing
-------

Routers can be created by adding a :router property. For
example, to create a round robin actor, you can do:

```clojure
(import '(akka.routing RoundRobinActor))

(let [rr (actor
          (fn [msg] 
            (do-something-with msg))
          { :router (RoundRobinActor. 3) }]
  (! rr :test))
```

This can even work with the *with-state* macro, in which
case each actor uses a different atom (if you want a shared
variable, a ref is probably the way to go).

```clojure
(let [a (actor
          (with-state [count 0]
	    (fn [msg]
              (println count)
     	      (inc count)))
          { :router (RoundRobinActor. 2) })]
  (! a "hi")
  (! a "hi")
  (! a "hi"))
```

This time, "0" will be printed twice to the console followed
by "1". 


## License

Copyright (C) 2012

Distributed under the Eclipse Public License, the same as Clojure.
