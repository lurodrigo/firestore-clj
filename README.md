# firestore-clj

A Firestore API for Clojure. Provides tools for doing both single pulls and streaming real-time data.
This library is a thin wrapper over `com.google.firebase/firebase-admin`.

## Installation and Docs

Add to your `project.clj` dependencies:

```[polvo/firestore-clj "0.1.0"]```

You can read the docs on [clj-doc](https://cljdoc.org/d/polvo/firestore-clj/0.1.0/doc/readme).

## Getting started
You can use `client-with-creds` to get a client using credentials from a service account.

```clojure
(require '[firestore-clj.core :as db])

(def db (db/client-with-creds "/path/to/creds.json"))
```

If you are using it inside Google Cloud Platform services with appropriate service account permissions, 
you can just provide the project-id using `default-client`:

```clojure
(def db (db/default-client "project-id"))
```

## Writing data

We currently provide the methods `add`, `set` and `delete`.

## Queries

We provide the query functions below. 

| firestore-clj | Java API |
| --- | --- |
| `filter=` | `.whereEqualTo()` |
| `filter<` | `.whereLessThan()` |
| `filter<=` | `.whereLessThanOrEqualTo()` |
| `filter>` | `.whereGreaterThan()` |
| `filter>=` | `.whereGreaterThanOrEqualTo()` |
| `in` | `.whereIn() ` |
| `contains` | `.whereArrayContains() ` |
| `contains-any` | `.whereArrayContainsAny() ` |
| `order-by` | `.orderBy` |
| `take` | `.limit()` |

You can use `pull` to fetch the results as a map. Here's an example:

```clojure
(-> (collection db "positions")
    (filter= "exchange" "bitmex") 
    (order-by "instrument")
    (take 2)
    pull)
``` 

You can perform multiple equality filters using a map.

```clojure
(-> (collection db "positions")
    (filter= {"exchange" "bitmex" 
              "account" 1}) 
    pull)
```

You can use descending order using adding a `:desc`

```clojure
(-> (collection db "positions")
    (filter= "account" 1)
    (order-by "size" :desc) 
    pull)
```

If you have the appropriate indexes, you can `order-by` multiple columns:

```clojure
(-> (collection db "positions")
    (filter= "account" 1)
    (order-by "size" :desc "instrument") 
    pull)
```

## Real-time data

You can materialize a document/collection reference or query as an `atom` using `->atom`:

```clojure
(def at @(-> (collection db "positions")
             (filter= {"exchange" "bitmex" 
                       "account" 1}) 
             ->atom)) ; @'ing since ->atom returns a promise

@at
```

Once you're done, you can just `detach` it.

If you need a lower level utility, you can use `add-listener`. It takes a 2-arity function and merely reifies it
as an `EventListener`. `snapshot->data` may be useful. Read original docs [here](https://firebase.google.com/docs/firestore/query-data/listen#events-local-changes) 
for more.

## Contributing and improvements

We welcome [PRs](https://github.com/polvotech/firestore-clj/compare). Here are some things we didn't implement:

* Updates and merges
* Transactions
* Batched writes
* Data pagination and cursors

## License

Copyright © 2020 Polvo Technologies. 

Distributed under the MIT License