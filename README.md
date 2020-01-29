# firestore-clj

A Firestore API for Clojure. Provides tools for doing both single pulls and streaming real-time data.
This library is a thin wrapper over `com.google.firebase/firebase-admin`. All functions are properly
type hinted, so no reflection is used. We also try to provide somewhat idiomatic names for the 
operations and queries.

## Installation and Docs

Add to your `project.clj` dependencies:

```[polvo/firestore-clj "0.1.3"]```

You can read the docs on [clj-doc](https://cljdoc.org/d/polvo/firestore-clj/0.1.3/doc/readme).

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

We currently provide the methods `add!`, `set!`, `assoc!`, `dissoc!`, `merge!` and `delete!`. 
Additionally, the functions `server-timestamp`, `inc`, `delete`, 
`array-union` and `array-remove` can be used as special values on a `set!`, `merge!` and `assoc!`. Some examples:

```clojure
; creates new document with random id
(-> (f/collection db "accounts")
    (f/add! {"name"     "account-x"
             "exchange" "bitmex"}))

; creates new document with id "xxxx"
(-> (f/collection db "accounts")
    (f/set! "xxxx" {"name"        "account-x"
                    "exchange"    "bitmex"
                    "start_date"  (f/server-timestamp)}))

(def doc (-> (f/collection db "accounts") 
             (f/document "xxx")))

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
(-> (f/collection db "positions")
    (f/filter= "exchange" "bitmex") 
    (f/sort-by "instrument")
    (f/take 2)
    f/pull)
``` 

You can perform multiple equality filters using a map.

```clojure
(-> (f/collection db "positions")
    (f/filter= {"exchange" "bitmex" 
                "account" 1}) 
    f/pull)
```

You can sort results. For descending order, add a `:desc`

```clojure
(-> (f/collection db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size") ; descending: (f/sort-by "size" :desc) 
    f/pull)
```

If you have the appropriate indexes, you can `order-by` multiple columns:

```clojure
(-> (f/collection db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size" :desc "instrument") 
    f/pull)
```

## Real-time data

You can materialize a document/collection reference or query as an `atom` using `->atom`:

```clojure
(def at (-> (f/collection db "positions")
            (f/filter= {"exchange" "bitmex" 
                        "account" 1}) 
            f/->atom))

(println @at)

; do stuff ...

(f/detach at) ; when you don't need updates anymore.
```

If you need a lower level utility, you can use `add-listener`. It takes a 2-arity function and merely reifies it
as an `EventListener`. `snapshot->data` may be useful. Read original docs [here](https://firebase.google.com/docs/firestore/query-data/listen#events-local-changes) 
for more.

## Design decisions 

* All operations that were non-blocking (returned ApiFutures) in the Java API are blocking here. If you want to go 
async, wrap futures.
* We assume all maps have string keys. We do not convert keywords. Use 
[`camel-snake-kebab`](https://clj-commons.org/camel-snake-kebab/) if you want to.

## Contributing and improvements

We welcome [PRs](https://github.com/polvotech/firestore-clj/compare). Here are some things that need some work:

* Transactions
* Batched writes
* Data pagination and cursors

## License

Copyright © 2020 Polvo Technologies. 

Distributed under the MIT License