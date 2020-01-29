(defproject polvo/firestore-clj "0.1.3"
  :description "A Firestore client for Clojure. Provides tools for both pull and realtime data."
  :url "https://github.com/polvotech/firestore-clj"
  :license {:name "MIT LICENSE"
            :url "https://github.com/polvotech/firestore-clj/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.google.firebase/firebase-admin "6.12.1"]]
  :repl-options {:init-ns firestore-clj.core})
