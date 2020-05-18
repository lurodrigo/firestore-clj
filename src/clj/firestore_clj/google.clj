(ns firestore-clj.google
  (:require
    [firestore-clj.google.fake-credentials :as fake-credentials])
  (:import
    (clj.firebase.google EmulatorChannelConfigurator FakeCredentials)
    (com.google.api.gax.grpc InstantiatingGrpcChannelProvider)
    (com.google.api.gax.rpc FixedHeaderProvider)
    (com.google.api.core ApiFunction)
    (com.google.auth Credentials)
    (com.google.auth.oauth2 GoogleCredentials)
    (com.google.cloud.firestore Firestore
                                FirestoreOptions
                                FirestoreOptions$Builder)))

(defn mk-fake-credentials
  []
  ^Credentials
  (FakeCredentials.))

(defn- mk-fixed-header-provider
  []
  (FixedHeaderProvider/create {"Authorization" "Bearer owner"}))

(defn- mk-emulator-channel-configurator
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

