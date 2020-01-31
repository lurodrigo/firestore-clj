# firestore-clj 
[![Clojars Project](https://img.shields.io/clojars/v/polvo/firestore-clj.svg)](https://clojars.org/polvo/firestore-clj)
[![cljdoc badge](https://cljdoc.org/badge/polvo/firestore-clj)](https://cljdoc.org/d/polvo/firestore-clj/CURRENT)

A Firestore API for Clojure. Provides tools for doing single pulls and writes, streaming real-time data,
batched writes and transactions.
This lib is a wrapper over `com.google.firebase/firebase-admin`. All functions are properly
type hinted, so no reflection is used. We also try to provide somewhat idiomatic names for the 
operations and queries, and idiomatic transactions as well.

## Getting started

You can use `client-with-creds` to get a client using credentials from a service account.

```clojure
(require '[firestore-clj.core :as f])

(def db (f/client-with-creds "/path/to/creds.json"))
```

If you are using it inside Google Cloud Platform services with appropriate service account permissions, 
you can just provide the project-id using `default-client`:

```clojure
(def db (f/default-client "project-id"))
```

## Writing data

We provide the methods `add!`, `set!`, `create!`, `assoc!`, `dissoc!`, `merge!` and `delete!`. 
Additionally, the functions `server-timestamp`, `inc`, `mark-for-deletion`, 
`array-union` and `array-remove` can be used as special values on a `set!`, `merge!` and `assoc!` operation. Some examples:

```clojure
; creates new document with random id
(-> (f/coll db "accounts")
    (f/add! {"name"     "account-x"
             "exchange" "bitmex"}))

; gets reference for document "xxxx", which may or may not exist
(def doc (-> (f/coll db "accounts")
             (f/doc "xxxx")))

; creates it (or overwrites it if it already exists)
(f/set! doc {"name"        "account-x"
             "exchange"    "bitmex"
             "start_date"  (f/server-timestamp)})

; updates one or more fields
(f/assoc! doc "trade_count" 0)

; updates one or more fields using a map
(f/merge! doc {"trade_count" (f/inc 1)
               "active"      true})

; deletes fields
(f/dissoc! doc "trade_count" "active")

; deletes it
(f/delete! doc)
```

## Queries

We provide the query functions below (along with corresponding Java API methods):

| firestore-clj | Java API |
| --- | ---  |
| `filter=`      | `.whereEqualTo()` |
| `filter<`      | `.whereLessThan()` |
| `filter<=`     | `.whereLessThanOrEqualTo()` |
| `filter>`      | `.whereGreaterThan()` |
| `filter>=`     | `.whereGreaterThanOrEqualTo()` |
| `filter-in`           | `.whereIn() ` |
| `filter-contains`     | `.whereArrayContains() ` |
| `filter-contains-any` | `.whereArrayContainsAny() ` |
| `sort-by`     | `.orderBy()` |
| `take`         | `.limit()` |

You can use `pull` to fetch the results as a map. Here's an example:

```clojure
(-> (f/coll db "positions")
    (f/filter= "exchange" "bitmex") 
    (f/sort-by "instrument")
    (f/take 2)
    f/pull)
``` 

You can perform multiple equality filters using a map.

```clojure
(-> (f/coll db "positions")
    (f/filter= {"exchange" "bitmex" 
                "account"  1}) 
    f/pull)
```

You can sort results. For descending order, add a `:desc`

```clojure
(-> (f/coll db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size") ; descending: (f/sort-by "size" :desc) 
    f/pull)
```

If you have the appropriate indexes, you can `sort-by` multiple fields:

```clojure
(-> (f/coll db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size" :desc "instrument") 
    f/pull)
```

## Real-time data

You can materialize a document/collection reference or query as an `atom` using `->atom`:

```clojure
(def at (-> (f/coll db "positions")
            (f/filter= {"exchange" "bitmex" 
                        "account"  1}) 
            f/->atom))

(println @at)

; do stuff ...

(f/detach at) ; when you don't need updates anymore.
```

If you need a lower level utility, you can use `add-listener`. It takes a 2-arity function and merely reifies it
as an `EventListener`. `snapshot->data` may be useful. Read original docs [here](https://firebase.google.com/docs/firestore/query-data/listen#events-local-changes) 
for more.

## Batched writes and transactions

The functions `set`, `assoc`, `merge`, `dissoc`, and `delete` are like their 
bang-ending counterparts, but merely describe operations to be done in 
a batched write/transaction context. They also return the batch/transaction itself, 
so you can easily chain operations. They are executed atomically by calling
`commit!` or `transact!`. 

```clojure
(let [[acc1 acc2 acc3] (-> (f/coll db "accounts")
                           (f/pull-all))]
  (-> (f/batch db)
      (f/assoc acc1 "tx_count" 0)
      (f/merge acc2 {"tx_count" 0})
      (f/delete acc3)
      (f/commit!)))
```

If you need reads, you'll need a transaction. Here's how you would transfer
balances between two accounts:

```clojure
(f/transact! db (fn [tx]
                  (let [[mine yours :as docs] (-> (f/coll db "accounts")
                                                  (f/docs ["my_account" "your_account"]))
                        [my-acc your-acc] (f/pull-all docs tx)]
                    (f/set tx mine (-> (update my-acc "balance" + 100)
                                       (update "tx_count" inc)))
                    (f/set tx yours (-> (update your-acc "balance" - 100)
                                        (update "tx_count" inc))))))
```

You can use both `pull` and `pull-all` in a transaction, passing the `Transaction` object as the second parameter.

## Conveniences

We've also written a few convenience functions for common types of transactions. 

### Updating a single field:

```clojure
(f/update-field! db
                 (-> (f/coll db "accounts")
                     (f/doc "my_account"))
                 "balance" * 2)
```

### Updating an entire doc

```clojure
(f/update! db
           (-> (f/coll db "accounts")
               (f/doc "my_account"))
           #(-> (update % "balance" * 2)
                (update "tx_count" inc)))
```

### Updating many docs in a single transaction

Over a vector of document references:

```clojure
(f/map! db (-> (f/coll db "accounts")
               (f/docs ["my_account" "your_account"]))
        #(update % "balance" * 2))
```

Over results of a query:

```clojure
(f/map! db (-> (f/coll db "accounts")
               (f/filter< "balance" 1000))
        #(assoc % "balance" 1000))
```

## Design decisions 

* Many operations that were async by default on the Java API are sync here. That's mainly because you can't block
`ApiFuture`s with `deref`. If you want to go async, simply wrap with `future`.
* We assume all maps have string keys. We do not convert keywords. You can use
[`camel-snake-kebab`](https://clj-commons.org/camel-snake-kebab/) for doing conversions.

## Contributing and improvements

We welcome [PRs](https://github.com/polvotech/firestore-clj/compare). Here are some things that need some work:

* Handle subcollections
* Data pagination and cursors
* More convenience around the objects returned from operations. Right now we simply return the boring underlying
Java objects.

## License

Copyright © 2020 Polvo Technologies. 

Distributed under the MIT License