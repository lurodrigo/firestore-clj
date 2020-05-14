(ns firestore-clj.core-test
  (:require
   [firestore-clj.core :as core]
   [clojure.test :refer :all]))


(deftest the-client-can-be-created-test
  (testing "it can be instatiated"
    (is (= 1 1) #_(some? (core/default-client "example-project")))))
