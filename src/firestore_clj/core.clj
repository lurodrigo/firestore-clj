(ns firestore-clj.core
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s])
  (:import (com.google.auth.oauth2 GoogleCredentials)
           (com.google.cloud.firestore Firestore QuerySnapshot CollectionReference EventListener DocumentReference DocumentSnapshot Query$Direction FieldValue Query ListenerRegistration)
           (com.google.firebase FirebaseApp FirebaseOptions FirebaseOptions)
           (com.google.firebase.cloud FirestoreClient)
           (java.util HashMap List)
           (com.google.cloud Timestamp)
           (com.google.api.core ApiFuture)))

(defn- build-hash-map
  "Helper for build java.util.HashMap without reflection."
  [m]
  (reduce (fn [^HashMap h [k v]]
            (do
              (.put h k v)
              h))
          (HashMap.)
          m))

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

(defn- document-snapshot->data
  "Gets a DocumentSnapshot's underlying data"
  [^DocumentSnapshot s]
  (->> (.getData s)
       (map (fn [[k v]]
              (if (instance? Timestamp v)
                [k (.toDate ^Timestamp v)]
                [k v])))
       (into {})))

(defn snapshot->data
  "Gets a DocumentSnapshot/CollectionSnapshot/QuerySnapshot's underlying data."
  [s]
  (if (instance? DocumentSnapshot s)
    (document-snapshot->data s)
    (->> s
         (map (fn [d]
                [(.getId ^DocumentSnapshot d) (document-snapshot->data d)]))
         (into {}))))

(defn pull
  "Pulls data from a DocumentReference or Query."
  [q]
  (if (instance? Query q)
    (snapshot->data (.get ^ApiFuture (.get ^Query q)))
    (snapshot->data (.get ^ApiFuture (.get ^DocumentReference q)))))

(defn add-listener
  "Adds a snapshot listener to a DocumentReference or Query.

  Listener is a fn of arity 2. First arg is the QuerySnapshot, second arg is a FirestoreException.
  Returns an ListenerRegistration object."
  [q f]
  (let [listener (reify
                   EventListener
                   (onEvent [_ s e]
                     (f s e)))]
    (if (instance? Query q)
      (.addSnapshotListener ^Query q listener)
      (.addSnapshotListener ^DocumentReference q listener))))

(defn ->atom
  "Returns an atom holding the latest value of a DocumentReference or Query."
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
     @d)))

(defn detach
  "Detaches an atom built with ->atom."
  [a]
  (.remove ^ListenerRegistration (:registration (meta a))))

(defn collection
  "Returns a CollectionReference for the collection of given name."
  ^CollectionReference [^Firestore db ^String coll-name]
  (.collection db coll-name))

(defn document
  "Gets a document from a collection reference."
  ^DocumentReference [^CollectionReference c ^String id]
  (.document c id))

(defn id
  "Returns the id of a document, given a reference."
  [^DocumentReference d]
  (.getId d))

(defn add!
  "Adds a document to a collection. Its id will be automatically generated."
  [^CollectionReference c m]
  (-> c (.add (build-hash-map m)) (.get)))

(defn set!
  "Creates or overwrites a document."
  [^CollectionReference c doc-name m]
  (-> c (.document doc-name) (.set (build-hash-map m)) (.get)))

(defn delete!
  "Deletes a document."
  [^DocumentReference d]
  (-> (.delete d) (.get)))

(defn merge!
  "Updates fields of a document."
  [^DocumentReference d m]
  (.get (.update d (build-hash-map m))))

(declare delete)

(defn assoc!
  "Associates new keys and values."
  [^DocumentReference d & kvs]
  (merge! d (apply hash-map kvs)))

(defn dissoc!
  "Deletes keys."
  [^DocumentReference d & ks]
  (->> ks
       (map (fn [k] [k (delete)]))
       (into {})
       (merge! d)))

(defn filter=
  "Filters where field = value. A map may be used for checking multiple equalities."
  ([^Query q m]
   (reduce (fn [q' [field value]]
             (filter= q' field value))
           q
           m))
  ([^Query q ^String field value]
   (.whereEqualTo q field value)))

(defn filter<
  "Filters where field < value."
  [^Query q ^String field value]
  (.whereLessThan q field value))

(defn filter<=
  "Filters where field <= value."
  [^Query q ^String field value]
  (.whereLessThanOrEqualTo q field value))

(defn filter>
  "Filters where field > value."
  [^Query q ^String field value]
  (.whereGreaterThan q field value))

(defn filter>=
  "Filters where field >= value."
  [^Query q ^String field value]
  (.whereGreaterThanOrEqualTo q field value))

(defn take
  "Limits results to a certain number."
  [^Query q n]
  (.limit q n))

(defn array-union
  "Used with `set!` and `merge!`. Adds unique values to an array field."
  [& vs]
  (FieldValue/arrayUnion (into-array vs)))

(defn array-remove
  "Used with `set!` and `merge!`. Removes values from an array field."
  [& vs]
  (FieldValue/arrayRemove (into-array vs)))

(defn server-timestamp
  "Used with `set!` and `merge!`. Timestamp for when the update operation is performed on server."
  []
  (FieldValue/serverTimestamp))

(defn inc
  "Used with `set!` and `merge!`. Increments a numeric field."
  [v]
  (if (int? v)
    (FieldValue/increment ^long v)
    (FieldValue/increment ^double v)))

(defn delete
  "Used with `set!` and `merge!`. A sentinel value that marks a field for deletion."
  []
  (FieldValue/delete))

(defn filter-in
  "Filters where field is one of the values in arr."
  [^Query q ^String field ^List arr]
  (.whereIn q field arr))

(defn filter-contains
  "Filters where field contains value."
  [^Query q ^String field value]
  (.whereArrayContains q field value))

(defn filter-contains-any
  "Filters where field contains one of the values in arr."
  [^Query q ^String field ^List arr]
  (.whereArrayContainsAny q field arr))

(s/def ::direction #{:asc :desc})
(s/def ::ordering (s/+ (s/alt
                         :field-direction (s/cat :field string? :direction ::direction)
                         :field string?)))

(defn order-by
  "Orders by a sequence of fields with optional directions. Notice that ordering by multiple fields
  requires creation of a composite index."
  [^Query q & ordering]
  (let [conformed (s/conform ::ordering ordering)]
    (if (= conformed ::s/invalid)
      (throw (ex-info "Invalid ordering: " (s/explain-str ::ordering ordering)))
      (reduce (fn [^Query q' [spec value]]
                (case spec
                  :field (.orderBy q' ^String value)
                  :field-direction (let [{:keys [field direction]} value]
                                     (if (= direction :desc)
                                       (.orderBy q' ^String field Query$Direction/DESCENDING)
                                       (.orderBy q' ^String field)))))
              q
              conformed))))