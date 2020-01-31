(defproject polvo/firestore-clj "0.3.0"
  :description "A Firestore API for Clojure. Provides tools for doing single pulls and writes, streaming real-time data, batched writes and transactions."
  :url "https://github.com/polvotech/firestore-clj"
  :license {:name "MIT LICENSE"
            :url "https://github.com/polvotech/firestore-clj/blob/master/LICENSE"}
  :dependencies [[org.clojure/core.match "0.3.0"]
                 [com.google.firebase/firebase-admin "6.12.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]]}})
