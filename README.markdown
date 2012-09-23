
akka-clojure
============

Description
-----------

akka-clojure is a simple Clojure wrapper for Akka.

Usage
-----

Actors are created with the actor function in akka-clojure.core, which
takes a callback function with two parameters. The first parameter is
the message which has been received. The second is the actor's state,
and will be explained later. The example below shows the basic usage.

```clojure
(use '(akka-clojure core))

(let [a (actor (fn [msg _] (println "Received " msg)))]
     (! a "hello"))
```

The purpose of the state parameter in the actor's callback is to allow
the actor to carry state between invocations. The result of the callback
becomes the next state. For example,

```clojure
(let [a (actor (fn [msg count]
     	       	   (println count)
     	       	   (+ count 1))
	       {:initial-state 0})]
     (! a "hi")
     (! a "hi"))    
```

will result in "0" and "1" being printed.



## License

Copyright (C) 2012 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
