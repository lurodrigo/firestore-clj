(ns firestore-clj.core
  (:require [clojure.java.io :as io]
            [byte-streams :as bs]
            [cheshire.core :as cheshire]
            [environ.core :refer [env]]
            [polvo.config :as config]
            [manifold.deferred :as d])
  (:import (com.google.auth.oauth2 GoogleCredentials)
           (com.google.cloud.firestore Firestore QuerySnapshot CollectionReference EventListener DocumentReference DocumentSnapshot)
           (com.google.firebase FirebaseApp FirebaseOptions FirebaseOptions)
           (com.google.firebase.cloud FirestoreClient)))

(defn- init-firestore-db! ^Firestore []
  (if (env :local?)
    (with-open [sa (io/input-stream (env :google-application-credentials))]
      (let [options (-> (FirebaseOptions/builder)
                        (.setCredentials (GoogleCredentials/fromStream sa))
                        (.build))]
        (FirebaseApp/initializeApp options)
        (reset! firestore-db (FirestoreClient/getFirestore))))

    (let [options (-> (FirebaseOptions/builder)
                      (.setCredentials (GoogleCredentials/getApplicationDefault))
                      (.setProjectId (env :project-id))
                      (.build))]
      (FirebaseApp/initializeApp options)
      (reset! firestore-db (FirestoreClient/getFirestore)))))

(defn ->data
  "Gets a DocumentReference/CollectionReference/Query's underlying data."
  [s]
  (if (instance? DocumentSnapshot s)
    (into {} (.getData s))
    (->> s
         (map (fn [d]
                [(.getId d) (into {} (.getData d))]))
         (into {}))))

(defn add-listener
  "Adds a snapshot listener to a DocumentReference/CollectionReference/Query.

  Listener is a fn of arity 2. First arg is the QuerySnapshot, second arg is a FirestoreException.
  Returns an ListenerRegistration object."
  [q f]
  (.addSnapshotListener q
                        (reify
                          EventListener
                          (onEvent [_ s e]
                            (f s e)))))

(defn ->atom
  "Returns a deferred yielding an atom holding the latest value of a CollectionReference/DocumentReference/Query."
  ([q]
   (->atom q identity))
  ([q error-handler]
   (let [a            (atom nil)
         registration (add-listener q (fn [s e]
                                        (if (some? e)
                                          (error-handler e)
                                          (reset! a (->data s)))))
         d            (d/deferred)]
     (alter-meta! a assoc :registration registration)
     (add-watch a :waiting-first-val (fn [_ _ _ _]
                                       (do
                                         (remove-watch a :waiting-first-val)
                                         (d/success! d a))))
     d)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
