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

## Collections, documents, and subcollections

`doc`, `docs`, `coll`, `colls` and `coll-group` are the basic functiones here.

`doc` gets a reference for the doc with given path relative to its argument, or a reference to a new one if the argument is 
a collection and no path is given. `coll` gets a collection (relative to root) or subcollection (relative to a document). 


```clojure
(f/doc db "accounts/account1")
(f/doc (f/coll db "accounts") "account1") ; same as above
(f/doc db "accounts/account1/subcoll1/subdoc1") ; nesting is allowed
(f/doc (f/coll db "accounts")) ; reference to a new document with auto-generated id 

(f/coll db "accounts/account1/subcoll1")
(f/coll (f/doc db "accounts/account1/") "subcoll1") ; same as above
```

`docs` gets all documents of a collection, or maps over `doc` if a sequence of paths is given.
`colls` gets all collections (relative to root) or subcolletions (relative to a document), or maps over `coll` if a sequence of paths is given

```clojure
(f/docs (f/coll db "accounts")) ; all documents from accounts
(f/docs (f/coll db "accounts") ["account1" "account2"]) ; these two documents from accounts
(f/docs db ["accounts/account1" "accounts/account2"])

(f/colls db) ; all collections at root level
(f/colls (f/doc "accounts/account1")) ; all subcollections
(f/colls db ["accounts" "positions"])
```

`coll-group` returns a query including docs in all collections or subcollections with a given id.

```clojure
(f/coll-group db "subcoll")
```

## Writing data

We provide the methods `add!`, `set!`, `create!`, `assoc!`, `dissoc!`, `merge!` and `delete!`. 
Additionally, the functions `server-timestamp`, `inc`, `mark-for-deletion`, 
`array-union` and `array-remove` can be used as special values on a `set!`, `merge!` and `assoc!` operation. 
`set!` (and its transactional/batch counterpart `set`) can receive a `:merge` to merge fields instead
 of overwriting, and a `:merge-fields` to specify which fields to merge. Some examples:

```clojure
; creates new document with random id
(-> (f/coll db "accounts")
    (f/add! {"name"     "account-x"
             "exchange" "bitmex"}))

(def doc (-> (f/doc "accounts/xxxx")
             (f/set! {"name"        "account-x"
                      "exchange"    "bitmex"
                      "start_date"  (f/server-timestamp)}) ; creates doc (or overwrites it it already exists)
             (f/assoc! "trade_count" 0) ; updates one or more fields
             (f/merge! {"trade_count" (f/inc 1)
                        "active"      true}) ; updates one or more fields using a map
             (f/dissoc! "trade_count" "active"))) ; deletes fields

; deletes doc
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
| `start-at` | `.startAt()` |
| `start-after` | `.startAfter()` |
| `end-at` | `.endAt()` |
| `end-before` | `.endBefore()` |
| `select` | `.select()` |
| `sort-by`     | `.orderBy()` |
| `limit`         | `.limit()` |
| `offset`         | `.offset()` |
| `range`          | `.offset().limit()` |

**Gotchas:** `limit` and  `offset` don't have the same semantics of `take` and `drop`. They are commutative,
so both `(-> q (t/offset 2) (t/limit 3))` and `(-> q (t/limit 3) (t/offset 2))` will return query results at positions
2, 3, 4. Also, you can't chain multiple offsets and limits, only the last call to each is valid.

You can use `pull` to fetch the results as a map. Here's an example:

```clojure
(-> (f/coll db "positions")
    (f/filter= "exchange" "bitmex")
    (f/limit 2)
    f/pull)
``` 

You can perform multiple equality filters using a map.

```clojure
(-> (f/coll db "positions")
    (f/filter= {"exchange" "bitmex" 
                "account"  1}) 
    f/pull)
```

When result ordering matters, you can use `pullv` to get the results as vectors, or `pullv-with-ids` if you
also need the ids.

```clojure
(-> (f/coll db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size") ; descending: (f/sort-by "size" :desc) 
    (f/start-at 10) ; ignore residual positions
    f/pullv) ; 
```
If you have the appropriate indexes, you can `sort-by` multiple fields:

```clojure
(-> (f/coll db "positions")
    (f/filter= "account" 1)
    (f/sort-by "size" :desc "instrument") 
    f/pull)
```

## Real-time data

You can materialize a document/collection reference or query as an `atom` with `->atom`...,
or stream updates as a [Manifold](https://github.com/ztellman/manifold) stream with `->stream`:

```clojure
(def at (-> (f/coll db "positions")
            (f/filter= {"exchange" "bitmex" 
                        "account"  1}) 
            f/->atom))

(println @at)

; do stuff ...

(f/detach at) ; when you don't need updates anymore.
```

```clojure
(require '[manifold.stream :as st])

(def stream (-> (f/coll db "positions")
                (f/filter= {"exchange" "bitmex" 
                            "account"  1}) 
                f/->stream))

(st/consume println stream)

(st/close! stream) ; when you don't need updates anymore.
```

Both `->atom` and `->stream` can also take a map with keys `error-handler` and a `plain-fn` that takes a snapshot
and returns clojure data. Built-in plain-fns are `ds->plain` and `ds->plain-with-id` for document
snapshots and `qs->plain-map`, `qs->plainv` and `qs->plainv-with-ids` for query snapshots. Default 
is `snap->plain`, which uses `ds->plain` for documents and `qs-plain-map` for queries. Of course,
you can pass `identity` if you just want the underlying snapshot.

If you need a lower level utility, you can use `add-listener`. It takes a 2-arity 
function and merely reifies it as an `EventListener`. The function `changes` might be useful:
it takes a snapshot and generates a vector of changes, with `:type`, `:reference`, `:new-index`
and `:old-index` keys. An example that just prints the ids of added, removed or modified docs.

```clojure
(-> (f/coll db "accounts")
    (f/add-listener (fn [s e]
                      (doseq [{:keys [type reference]} (f/changes s)]
                        (case type
                          :added    (println "Added doc:" (f/id reference))
                          :modified (println "Modified doc:" (f/id reference))
                          :removed  (println "Deleted doc:" (f/id reference)))))))
```

Read upstream docs 
[here](https://firebase.google.com/docs/firestore/query-data/listen#events-local-changes) for more.

## Batched writes and transactions

The functions `set`, `assoc`, `merge`, `dissoc`, and `delete` are like their 
bang-ending counterparts, but merely describe operations to be done in 
a batched write/transaction context. They also return the batch/transaction itself, 
so you can easily chain operations. They are executed atomically by calling
`commit!` or `transact!`. 

```clojure
(let [[acc1 acc2 acc3] (-> (f/coll db "accounts")
                           (f/docs ["acc1" "acc2" "acc3"]))]
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
                        [my-acc your-acc] (f/pull-docs docs tx)]
                    (f/set tx mine (-> (update my-acc "balance" + 100)
                                       (update "tx_count" inc)))
                    (f/set tx yours (-> (update your-acc "balance" - 100)
                                        (update "tx_count" inc))))))
```

You can use both `pull` and `pull-docs` in a transaction, passing the `Transaction` object as the second parameter.

## Conveniences

We've also written a few convenience functions for common types of transactions and batches writes. 

### Updating a single field:

```clojure
(f/update-field! (-> (f/coll db "accounts")
                     (f/doc "my_account"))
                 "balance" * 2)
```

### Updating an entire doc

```clojure
(f/update! (-> (f/coll db "accounts")
               (f/doc "my_account"))
           #(-> (update % "balance" * 2)
                (update "tx_count" inc)))
```

### Updating many docs in a single transaction

Over a vector of document references:

```clojure
(f/map! (-> (f/coll db "accounts")
            (f/docs ["my_account" "your_account"]))
        #(update % "balance" * 2))
```

Over results of a query:

```clojure
(f/map! (-> (f/coll db "accounts")
            (f/filter< "balance" 1000))
        #(assoc % "balance" 1000))
```

### Deleting multiple docs

In most cases `delete-all!` is enough. It accepts queries, including collections. It
queries and writes in batches for efficiency.

```clojure
(f/delete-all! (f/coll db "accounts"))
(f/delete-all! (-> (f/coll db "accounts")
                   (f/filter= "exchange" "deribit")))
```

However, it doesn't work if the queries contain limits or offsets, since they can't be chained
and they are used internally for batching. In this case, use `delete-all!*`. It 
fetches all query results once and deletes in batches, therefore potentially consuming
more memory for a while. 

```clojure
(f/delete-all!* (-> (f/coll db "accounts")
                    (f/limit 3)))
```

A more generic function is `batch-delete!`, which deletes an arbitrary seq of document references
in batches. You can also use `purge!`, which deletes documents, collections and queries recursively.

## Idioms

You might notice that most signatures use the same names for its arguments. Most of them are type-hinted, 
but anyways, here are the conventions we follow: 

| Names | Expected Object |
| --- | ---  |
| `db` | `Firestore` |
| `dr` | `DocumentReference` |
| `cr` | `CollectionReference` |
| `q`  | `QuerySnapshot` |
| `ds` | `DocumentSnapshot` or `QueryDocumentSnapshot` |
| `t`  | `Transaction` |
| `b`  | `WriteBatch` |
| `context`   | `UpdateBuilder` (either `WriteBatch` or `Transaction`) |
| `s`, `snap` | `QuerySnapshot`, `DocumentSnapshot` or `QueryDocumentSnapshot` | 
| `plain`     | plain clojure maps or vectors |
| `*->plain-something` | fns that turn `ds` or `qs` into plain data |
| `pull-something` | fns that turn `dr`/`cr`/`q` into `plain` data (thus querying db) * |

We sometimes opted for slightly longer names to avoid obfuscation. For example, `ds->plain` or `qs->plain-map` are fine, but
`qs->dss` would be terrible so we opted for `query-snap->doc-snaps`.

* Yes, pull fns are merely compositions of query-performing `snap`/`doc-snap`/`query-snap` with `->plain-something` fns provided for
convenience. They are good defaults, but sometimes we need finer control. For instance, if we need to keywordize 
values, we can write a simple `->plain-with-kw` fn and get a `pull-with-kw` merely `comp`ing with `snap`. That's a very neat
idiom if you need to do both common pulls and `qs` manipulation inside `add-listener`.

## Design decisions 

* Many operations that were async by default on the Java API are sync here, mainly because
in our context that's what made sense, avoiding lots of derefs. If you want to go async, simply 
wrap with `future` where appropriate.
* We assume all maps have string keys. We do not convert keywords. You can use
[`camel-snake-kebab`](https://clj-commons.org/camel-snake-kebab/) for doing conversions.

## Contributing and improvements

We welcome [PRs](https://github.com/polvotech/firestore-clj/compare). Here are some things that need some work:

* Preconditions
* More convenience around the objects returned from operations
* Define default behavior regarding conversions between `Timestamp` and `java.util.Date`. Currently we perform conversions
on reads (they are perfomed by the lib automatically on writes).

## License

Copyright © 2020 Polvo Technologies. 

Distributed under the MIT License.