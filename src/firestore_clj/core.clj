(ns firestore-clj.core
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s])
  (:import (com.google.auth.oauth2 GoogleCredentials)
           (com.google.cloud.firestore Firestore QuerySnapshot CollectionReference EventListener DocumentReference DocumentSnapshot Query$Direction)
           (com.google.firebase FirebaseApp FirebaseOptions FirebaseOptions)
           (com.google.firebase.cloud FirestoreClient)
           (java.util HashMap)))

(defn client-with-creds
  "Creates a client from a credentials JSON file."
  ^Firestore [creds-path]
  (with-open [sa (io/input-stream creds-path)]
    (let [options (-> (FirebaseOptions/builder)
                      (.setCredentials (GoogleCredentials/fromStream sa))
                      (.build))]
      (FirebaseApp/initializeApp options)
      (FirestoreClient/getFirestore))))

(defn default-client
  "Gets default client i.e. using a service account."
  ^Firestore [project-id]
  (let [options (-> (FirebaseOptions/builder)
                    (.setCredentials (GoogleCredentials/getApplicationDefault))
                    (.setProjectId project-id)
                    (.build))]
    (FirebaseApp/initializeApp options)
    (FirestoreClient/getFirestore)))

(defn snapshot->data
  "Gets a DocumentReference/CollectionReference/Query's underlying data."
  [s]
  (if (instance? DocumentSnapshot s)
    (into {} (.getData s))
    (->> s
         (map (fn [d]
                [(.getId d) (into {} (.getData d))]))
         (into {}))))

(defn pull
  "Pulls data from a DocumentReference, CollectionReference or Query."
  [q]
  (->> q
       (.get)
       (.get)
       snapshot->data))

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
  "Returns a promise yielding an atom holding the latest value of a CollectionReference/DocumentReference/Query."
  ([q]
   (->atom q identity))
  ([q error-handler]
   (let [a            (atom nil)
         registration (add-listener q (fn [s e]
                                        (if (some? e)
                                          (error-handler e)
                                          (reset! a (snapshot->data s)))))
         d            (promise)]
     (alter-meta! a assoc :registration registration)
     (add-watch a :waiting-first-val (fn [_ _ _ _]
                                       (do
                                         (remove-watch a :waiting-first-val)
                                         (deliver d a))))
     d)))

(defn detach
  "Detaches an atom built with ->atom."
  [a]
  (.remove (:registration (meta a))))

(defn collection
  "Returns a CollectionReference for the collection of given name."
  ^CollectionReference [^Firestore db ^String coll-name]
  (.collection db coll-name))

(defn document
  "Gets a document from a collection reference."
  ^DocumentReference [^CollectionReference c ^String id]
  (.document c id))

(defn add
  "Adds a document to a collection. Its id will be automatically generated. This is a blocking operation."
  [^CollectionReference c m]
  (-> c (.add (HashMap. m)) (.get)))

(defn set
  "Creates or overwrites a document. This is a blocking operation."
  [^CollectionReference c doc-name m]
  (-> c (.document doc-name) (.set (HashMap. m)) (.get)))

(defn delete
  "Deletes a document."
  [^DocumentReference d]
  (-> (.delete d) (.get)))

(defn filter=
  "Filters where field = value. A map may be used for checking multiple equalities."
  ([q m]
   (reduce (fn [q' [field value]]
             (filter= q' field value))
     q
     m))
  ([q field value]
   (.whereEqualTo q field value)))

(defn filter<
  "Filters where field < value."
  [q field value]
  (.whereLessThan q field value))

(defn filter<=
  "Filters where field <= value."
  [q field value]
  (.whereLessThanOrEqualTo q field value))

(defn filter>
  "Filters where field > value."
  [q field value]
  (.whereGreaterThan q field value))

(defn filter>=
  "Filters where field >= value."
  [q field value]
  (.whereGreaterThanOrEqualTo q field value))

(defn take
  "Limits results to a certain number."
  [q n]
  (.limit q n))

(defn in
  "Filters where field is one of the values in arr."
  [q field arr]
  (.whereIn q field arr))

(defn contains
  "Filters where field contains value."
  [q field value]
  (.whereArrayContains q field value))

(defn contains-any
  "Filters where field contains one of the values in arr."
  [q field arr]
  (.whereArrayContainsAny q field arr))

(s/def ::direction #{:asc :desc})
(s/def ::ordering (s/+ (s/alt
                         :field-direction (s/cat :field string? :direction ::direction)
                         :field string?)))

(defn order-by
  "Orders by a sequence of fields with optional directions. Notice that ordering by multiple fields
  requires creation of a composite index."
  [q & ordering]
  (let [conformed (s/conform ::ordering ordering)]
    (if (= conformed ::s/invalid)
      (throw (ex-info "Invalid ordering: " (s/explain-str ::ordering ordering)))
      (reduce (fn [q' [spec value]]
                (case spec
                  :field (.orderBy q' value)
                  :field-direction (let [{:keys [field direction]} value]
                                     (if (= direction :desc)
                                       (.orderBy q' field Query$Direction/DESCENDING)
                                       (.orderBy q' field)))))
              q
              conformed))))