(ns firestore-clj.core
  (:require [clojure.java.io :as io]
            [clojure.core.match :refer [match]]
            [clojure.spec.alpha :as s])
  (:import (com.google.auth.oauth2 GoogleCredentials)
           (com.google.cloud.firestore Firestore QuerySnapshot CollectionReference EventListener DocumentReference DocumentSnapshot Query$Direction FieldValue Query ListenerRegistration WriteBatch Transaction UpdateBuilder Transaction$Function TransactionOptions QueryDocumentSnapshot)
           (com.google.firebase FirebaseApp FirebaseOptions FirebaseOptions)
           (com.google.firebase.cloud FirestoreClient)
           (java.util HashMap List)
           (com.google.cloud Timestamp)
           (com.google.api.core ApiFuture)
           (java.util.concurrent Executor)))

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
  "Pulls data from a DocumentReference or Query. 2-arity fn operates inside a transaction context."
  ([q]
   (if (instance? Query q)
     (snapshot->data (.get ^ApiFuture (.get ^Query q)))
     (snapshot->data (.get ^ApiFuture (.get ^DocumentReference q)))))
  ([q ^Transaction t]
   (if (instance? Query q)
     (snapshot->data (.get ^ApiFuture (.get t ^Query q)))
     (snapshot->data (.get ^ApiFuture (.get t ^DocumentReference q))))))

(defn pull-all
  "Pulls data from a vector of `DocumentReference`s. 2-arity fn operates inside a transaction context."
  ([ds]
   (mapv #(snapshot->data (.get ^ApiFuture (.get ^DocumentReference %))) ds))
  ([ds ^Transaction t]
   (->> ds
        (into-array DocumentReference)
        (.getAll t)
        (.get)
        (mapv document-snapshot->data))))

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

(defn coll
  "Returns a CollectionReference for the collection of given name."
  ^CollectionReference [^Firestore db ^String coll-name]
  (.collection db coll-name))

(defn doc
  "Gets a DocumentReference given CollectionReference and an id. If id is not given, it will point
  to a new document with an auto-generated-id"
  (^DocumentReference [^CollectionReference c]
   (.document c))
  (^DocumentReference [^CollectionReference c ^String id]
   (.document c id)))

(defn docs
  "Gets a vector of `DocumentReference`s, given a vector of ids"
  ^DocumentReference [^CollectionReference c ds]
  (mapv #(.document c %) ds))

(defn id
  "Returns the id of a DocumentReference"
  [^DocumentReference d]
  (.getId d))

(defn add!
  "Adds a document to a collection. Its id will be automatically generated."
  [^CollectionReference c m]
  (-> c (.add (build-hash-map m)) (.get)))

(defn create!
  "Creates a new document at the DocumentReference's location. Fails if the document exists."
  [^DocumentReference d m]
  (-> d (.create (build-hash-map m)) (.get)))

(defn set!
  "Creates or overwrites a document."
  [^DocumentReference d m]
  (-> d (.set (build-hash-map m)) (.get)))

(defn set
  "Creates or overwrites a document in a batched write/transaction context."
  [^UpdateBuilder context ^DocumentReference d m]
  (.set context d (build-hash-map m)))

(defn delete!
  "Deletes a document."
  [^DocumentReference d]
  (-> (.delete d) (.get)))

(defn delete
  "Deletes a document in a batched write/transaction context."
  [^UpdateBuilder context ^DocumentReference d]
  (-> (.delete context d)))

(defn merge!
  "Updates fields of a document."
  [^DocumentReference d m]
  (-> d (.update (build-hash-map m)) (.get)))

(defn merge
  "Updates field of a document in a batched write/transaction context."
  [^UpdateBuilder context ^DocumentReference d m]
  (.update context d (build-hash-map m)))

(declare mark-for-deletion)

(defn batch
  "Get a new write batch"
  [^Firestore db]
  (.batch db))

(defn commit!
  "Commits a write batch."
  [^WriteBatch b]
  (-> (.commit b) (.get)))

(defn assoc!
  "Associates new keys and values."
  [^DocumentReference d & kvs]
  (merge! d (apply hash-map kvs)))

(defn assoc
  "Associates new keys and values in a batched write/transaction context."
  [context ^DocumentReference d & kvs]
  (merge context d (apply hash-map kvs)))

(defn dissoc!
  "Deletes keys."
  [^DocumentReference d & ks]
  (->> ks
       (map (fn [k] [k (mark-for-deletion)]))
       (into {})
       (merge! d)))

(defn dissoc
  "Deletes keys in a batched write/transaction context."
  [context ^DocumentReference d & ks]
  (->> ks
       (map (fn [k] [k (mark-for-deletion)]))
       (into {})
       (merge context d)))

(defn- tx-option
  "Creates a Transaction$Function."
  [f]
  (reify
    Transaction$Function
    (updateCallback [_ t]
      (f t))))

(defn transact!
  "Performs a transaction. Optionally, you can specify an executor and the maximum number of attemps."
  ([^Firestore db f]
   (.get (.runTransaction db (tx-option f))))
  ([^Firestore db f {:keys [attempts executor] :as options}]
   (.get (.runTransaction db
                          (tx-option f)
                          (match [attempts executor]
                                 [nil nil] (TransactionOptions/create)
                                 [nil _] (TransactionOptions/create ^int attempts)
                                 [_ nil] (TransactionOptions/create ^Executor executor)
                                 [_ _] (TransactionOptions/create executor attempts))))))

(defn update!
  "Updates a document by applying a function to it."
  ([^Firestore db ^DocumentReference d f & args]
   (transact! db (fn [tx]
                   (let [data (pull d tx)]
                     (set tx d (apply f data args)))))))

(defn update-field!
  "Updates a single field of a document by applying a function to it."
  [^Firestore db ^DocumentReference d field f & args]
  (update! db d (fn [data]
                    (apply update data field f args))))

(defn map!
  "Updates all docs in a vector or query by applying a function to them"
  [^Firestore db q f & args]
  (transact! db (fn [tx]
                  (let [ds (if (instance? Query q)
                             (let [doclist (.getDocuments ^QuerySnapshot (->> (.get ^Transaction tx ^Query q)
                                                                              (.get)))]
                               (mapv #(.getReference ^QueryDocumentSnapshot %) doclist))
                             q)
                        all-data (pull-all ds tx)]
                    (mapv #(set tx %1 (apply f %2 args)) ds all-data)))))

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

(defn mark-for-deletion
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