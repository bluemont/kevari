Kevari
======

Kevari implements a key-value store in Clojure. It uses Clojure agents
arranged in a ring. It is loosely inspired by Riak.

Kevari is an overgrown toy project. It is certainly not a 'real' key-value
store. It was fun a side project. Maybe you can get something useful out of
reading the code? Let me know if you have comments or suggestions.

Demonstration
-------------

Make a ring with 6 nodes with 3 copies of each key-value pair:

    (def ring (make-ring 6 3))

Add three key-value pairs:

    (put-ring ring :k1 :v1)
    (put-ring ring :k2 :v2)
    (put-ring ring :k3 :v3)

The processing and replication will happen asynchronously using a thread pool.

For such a short example, you probably won't have to wait, but it useful to know how to do it:

    (await-ring ring)

Now, you can check that the values can be retrieved:

    (get-ring ring :k1) ; :v1
    (get-ring ring :k2) ; :v2
    (get-ring ring :k3) ; :v3

If you want to see how the keys got distributed:

    (doseq [node (:nodes ring)]
      (let [n @node]
        (prn (:id n) (sort (keys (:store n))))))

You can see that each key lives on 3 nodes:

    0 (:k3)
    1 ()
    2 (:k2)
    3 (:k1 :k2)
    4 (:k1 :k2 :k3)
    5 (:k1 :k3)

Profiling
---------

To show performance across various n values:

    (print-n-scaling
      50   ; warm up
      5    ; trials
      1    ; n
      1    ; w
      5000 ; x (key-value pairs to load)
      [2 4 10 20 40 100 200 400 1000 2000 4000 10000 20000 40000 100000])

This shows that agents are relatively inexpensive:

    n=1 w=1 x=5000
    baseline=93.5 ms
        2 * x :     0.7 * baseline
        4 * x :     0.9 * baseline
       10 * x :     0.9 * baseline
       20 * x :     0.9 * baseline
       40 * x :     1.0 * baseline
      100 * x :     0.9 * baseline
      200 * x :     0.9 * baseline
      400 * x :     0.9 * baseline
     1000 * x :     0.9 * baseline
     2000 * x :     1.0 * baseline
     4000 * x :     1.2 * baseline
    10000 * x :     1.6 * baseline
    20000 * x :     2.2 * baseline
    40000 * x :     3.4 * baseline

To show performance across various w values:

    (print-w-scaling
      50   ; warm up
      5    ; trials
      1000 ; n
      1    ; w
      5000 ; x (key-value pairs to load)
      [2 4 8 10 20 40 80 100 200 400])

Inter-agent communication spikes when w is 200x of baseline:

    n=1000 w=1 x=5000
    baseline=87.2 ms
        2 * x :     1.1 * baseline
        4 * x :     1.2 * baseline
        8 * x :     1.4 * baseline
       10 * x :     1.4 * baseline
       20 * x :     2.1 * baseline
       40 * x :     3.1 * baseline
       80 * x :     5.5 * baseline
      100 * x :     7.2 * baseline
      200 * x :    29.5 * baseline
      400 * x :    ? (did not complete in less than 2 minutes)

To show performance across various x values:

    (print-x-scaling
      100  ; warm up
      5    ; trials
      5    ; n
      2    ; w
      1000 ; x (key-value pairs to load)
      [2 4 8 10 20 40 80])

Scaling appears mostly linear until x is 20x of baseline:

    n=5 w=2 x=1000
    baseline=44.1 ms
        2 * x :     2.0 * baseline
        4 * x :     4.4 * baseline
        8 * x :    10.1 * baseline
       10 * x :    12.8 * baseline
       20 * x :    35.2 * baseline
       40 * x :   114.7 * baseline
       80 * x :   481.7 * baseline

So, Kevari has scaling issues.

History
-------

I built Kevari shortly after attending RICON, a distributed systems conference
in New York City; it was inspired by a converation with a friend. The name
comes from the first two letters of each word of "Key Value Riak".

License
-------

Copyright © 2013 Bluemont Labs LLC

Distributed under the Eclipse Public License, the same as Clojure.
