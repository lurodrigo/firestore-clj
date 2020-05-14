(ns firestore-clj.google
  (:require
   [firestore-clj.google.fake-credentials :as fake-credentials])
  (:import
   (clj.firebase.google
    EmulatorChannelConfigurator
    FakeCredentials)
   (com.google.api.gax.grpc InstantiatingGrpcChannelProvider)
   (com.google.api.gax.rpc FixedHeaderProvider)
   (com.google.api.core ApiFunction)
   (com.google.auth
    Credentials)
   (com.google.auth.oauth2
    GoogleCredentials)
   (com.google.cloud.firestore
    Firestore
    FirestoreOptions
    FirestoreOptions$Builder)))

(defn mk-fake-credentials
  []
  ^Credentials
  (FakeCredentials.))

(defn mk-fixed-header-provider
  []
  (FixedHeaderProvider/create {"Authorization" "Bearer owner"}))

(defn mk-emulator-channel-configurator
  []
  ^ApiFunction
  (EmulatorChannelConfigurator.))

(defn mk-emulator-grpc-channel-provider
  ^InstantiatingGrpcChannelProvider
  [emulator-host]
  (let [p (-> (InstantiatingGrpcChannelProvider/newBuilder)
              (.setEndpoint emulator-host)
              (.setChannelConfigurator (mk-emulator-channel-configurator))
              (.setHeaderProvider (mk-fixed-header-provider))
              (.build))]
    p))

(defn emulator-client
  "Gets a client i.e. using the emulator"
  ^Firestore
  [project-id emulator-host]
  (let [credentials (mk-fake-credentials)
        channel-provider (mk-emulator-grpc-channel-provider emulator-host)
        ^FirestoreOptions options (->
                                   (FirestoreOptions/getDefaultInstance)
                                   (.toBuilder)
                                   ^FirestoreOptions$Builder
                                   (.setProjectId project-id)
                                   (.setChannelProvider channel-provider)
                                   ^FirestoreOptions$Builder
                                   (.setCredentials credentials)
                                   ^FirestoreOptions (.build))]
    (.getService options)))

(comment
  (require '[taoensso.timbre :as log])
  (require '[firestore-clj.core :as f])
  (def my-db (emulator-client "test-app-4" "localhost:8080"))
  (mk-fake-credentials)
  (mk-fixed-header-provider)

  (prn my-db)
  (-> (f/coll my-db "accounts")
      (f/add! {"name"     "account-x"
               "exchange" "bitmex"}))
  )
