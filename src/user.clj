(ns user
  (:require [firestore-clj.core :as f]
            [clojure.algo.generic.functor :refer [fmap]]))

(def db (f/client-with-creds (System/getenv "CREDENTIALS")))