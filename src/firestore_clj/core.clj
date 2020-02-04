(ns firestore-clj.core
  (:refer-clojure :exclude [set set! merge merge! assoc assoc! dissoc dissoc! take inc update!])
  (:require [clojure.java.io :as io]
            [clojure.core :as core]
            [clojure.core.match :refer [match]]
            [clojure.spec.alpha :as s])
  (:import (clojure.lang IAtom)
           (com.google.api.core ApiFuture)
           (com.google.auth.oauth2 GoogleCredentials)
           (com.google.cloud Timestamp)
           (com.google.cloud.firestore Firestore QuerySnapshot CollectionReference EventListener DocumentReference DocumentSnapshot Query$Direction FieldValue Query ListenerRegistration WriteBatch Transaction UpdateBuilder Transaction$Function TransactionOptions QueryDocumentSnapshot DocumentChange$Type DocumentChange)
           (com.google.firebase FirebaseApp FirebaseOptions FirebaseOptions)
           (com.google.firebase.cloud FirestoreClient)
           (java.util HashMap List)
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

(defn id
  "Returns the id of a CollectionReference, DocumentReference or DocumentSnapshot."
  [d]
  (condp instance? d
    DocumentReference (.getId ^DocumentReference d)
    DocumentSnapshot (.getId ^DocumentSnapshot d)
    CollectionReference (.getId ^CollectionReference d)))

(defn doc->plain
  "Represents a DocumentSnapshot as plain map."
  [^DocumentSnapshot s]
  (->> (.getData s)
       (map (fn [[k v]]
              (if (instance? Timestamp v)
                [k (.toDate ^Timestamp v)]
                [k v])))
       (into {})))

(defn doc->plain-with-id
  "Represents a DocumentSnapshot as a [id plain-doc] pair"
  [^DocumentSnapshot s]
  [(id s) (doc->plain s)])

(defn query->plainv
  "Represents a QuerySnapshot as a plain vector of document data."
  [^QuerySnapshot s]
  (mapv doc->plain s))

(defn query->plainv-with-ids
  "Represents a QuerySnapshot as a plain vector of [id doc] pairs."
  [^QuerySnapshot s]
  (mapv doc->plain-with-id s))

(defn query->plain-map
  "Represents a QuerySnapshot as a plain map whose keys are the document ids."
  [^QuerySnapshot s]
  (into {} (query->plainv-with-ids s)))

(defn snapshot->data
  "Gets a DocumentSnapshot/CollectionSnapshot/QuerySnapshot's underlying data."
  [s]
  (condp instance? s
    DocumentSnapshot (doc->plain s)
    QuerySnapshot (query->plain-map s)))

(defn doc-snapshot
  "Gets a QueryDocumentSnapshot given a DocumentReference and possibly a Transaction."
  ([^DocumentReference d]
   (.get ^ApiFuture (.get d)))
  ([^DocumentReference d ^Transaction t]
   (.get ^ApiFuture (.get t d))))

(defn query-snapshot
  "Gets a QuerySnapshot given a Query and possibly a Transaction."
  ([^Query q]
   (.get ^ApiFuture (.get q)))
  ([^Query q ^Transaction t]
   (.get ^ApiFuture (.get t q))))

(def ^{:doc "Pulls clojure data from a DocumentReference."}
  pull-doc (comp doc->plain doc-snapshot))

(def ^{:doc "Pulls results from a Query as clojure data."}
  pull-query (comp query->plain-map query-snapshot))

(def ^{:doc "Pulls query results as a vector of document data."}
  pullv (comp query->plainv query-snapshot))

(def ^{:doc "Pulls query results as a vector of pairs. Each pair has an id and document data"}
  pullv-with-ids (comp query->plainv-with-ids query-snapshot))

(defn pull-docs
  "Pulls clojure data from a sequence of `DocumentReference`s, possibly inside a transaction context."
  ([ds]
   (mapv pull-doc ds))
  ([ds ^Transaction t]
   (->> ds
        (into-array DocumentReference)
        (.getAll t)
        (.get)
        (mapv doc->plain))))

(defn pull
  "Pulls data from a DocumentReference or Query, possibly inside a transaction context."
  ([q]
   (condp instance? q
     Query (pull-query q)
     DocumentReference (pull-doc q)))
  ([q ^Transaction t]
   (condp instance? q
     Query (pull-query t q)
     DocumentReference (pull-doc t q))))

(defn add-listener
  "Adds a snapshot listener to a DocumentReference or Query.

  Listener is a fn of arity 2. First arg is the QuerySnapshot, second arg is a FirestoreException.
  Returns an ListenerRegistration object."
  [q f]
  (let [listener (reify
                   EventListener
                   (onEvent [_ s e]
                     (f s e)))]
    (condp instance? q
      Query (.addSnapshotListener ^Query q listener)
      DocumentReference (.addSnapshotListener ^DocumentReference q listener))))

(defn ->atom
  "Returns an atom holding the latest value of a DocumentReference or Query."
  ([q]
   (->atom q {}))
  ([q {:keys [error-handler plain-fn] :or {error-handler identity plain-fn snapshot->data}}]
   (let [a            (atom nil)
         registration (add-listener q (fn [s e]
                                        (if (some? e)
                                          (error-handler e)
                                          (reset! a (plain-fn s)))))
         d            (promise)]
     (alter-meta! a core/assoc :registration registration)
     (add-watch a :waiting-first-val (fn [_ _ _ _]
                                       (do
                                         (remove-watch a :waiting-first-val)
                                         (deliver d a))))
     @d)))

(defn detach
  "Detaches an atom built with `->atom` or a listener returned from `add-listener`."
  [a]
  (condp instance? a
    IAtom (.remove ^ListenerRegistration (:registration (meta a)))
    ListenerRegistration (.remove ^ListenerRegistration a)))

(def ^:private change-enum->change-kw {DocumentChange$Type/ADDED    :added
                                       DocumentChange$Type/REMOVED  :removed
                                       DocumentChange$Type/MODIFIED :modified})

(defn changes
  "Gets a list of changes."
  [^QuerySnapshot s]
  (mapv (fn [^DocumentChange dc]
          {:type      (change-enum->change-kw (.getType dc))
           :reference (some-> (.getDocument dc)
                              (.getReference))
           :new-index (.getNewIndex dc)
           :old-index (.getOldIndex dc)})
        (.getDocumentChanges s)))

(defn coll
  "Returns a CollectionReference for the collection of given name."
  ^CollectionReference [^Firestore db ^String coll-name]
  (.collection db coll-name))

(defn list-colls
  "Lists all collections"
  [^Firestore db]
  (into [] (.listCollections db)))

(defn doc
  "Gets a DocumentReference given CollectionReference and an id or Firestore and path. If id is not given, it will point
  to a new document with an auto-generated-id"
  (^DocumentReference [^CollectionReference c]
   (.document c))
  (^DocumentReference [c ^String id]
   (condp instance? c
     CollectionReference (.document ^CollectionReference c id)
     Firestore (.document ^Firestore c id))))

(defn docs
  "Gets a vector of `DocumentReference`s, given a vector of ids"
  [c ds]
  (mapv (partial doc c) ds))

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
                   (let [data (pull-doc d tx)]
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
                  (let [ds       (if (instance? Query q)
                                   (let [doclist (.getDocuments ^QuerySnapshot (.get ^ApiFuture (.get ^Transaction tx ^Query q)))]
                                     (mapv #(.getReference ^QueryDocumentSnapshot %) doclist))
                                   q)
                        all-data (pull-docs ds tx)]
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