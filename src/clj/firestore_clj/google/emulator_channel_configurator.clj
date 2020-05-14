(ns firestore-clj.google.emulator-channel-configurator
  (:gen-class
   :implements [com.google.api.core.ApiFunction]
   :name clj.firebase.google.EmulatorChannelConfigurator)
  (:import
   (io.grpc ManagedChannelBuilder)))

(defn -apply
  ^ManagedChannelBuilder
  [this ^ManagedChannelBuilder input]
  (.usePlaintext input))

