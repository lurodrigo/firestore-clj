# firestore-clj

A Firestore API for Clojure. Provides tools for doing both single pulls and streaming real-time data.
This library is a thin wrapper over `com.google.firebase/firebase-admin`.

## Usage

Add to your `project.clj` dependencies:

```[polvo/firestore-clj "0.1.2"]```

You can read the full docs (includes an overview!) at 
[clj-doc](https://cljdoc.org/d/polvo/firestore-clj/0.1.2/doc/readme),
but here's a quickie:

```clojure
(require '[firestore-clj.core :refer :all])

(def db (client-with-creds "/path/to/creds.json"))

(def query (-> (collection db "positions")
               (filter= "account" 1)
               (order-by "size" :desc "instrument")))

(pull query) ; for fetching data once

(->atom query) ; to materialize query results in an atom (and receive updates)
```

## License

Copyright © 2020 Polvo Technologies. 

Distributed under the MIT License.