(ns firestore-clj.google.fake-credentials
  (:gen-class
   :extends com.google.auth.Credentials
   :name clj.firebase.google.FakeCredentials)
  (:import
   (com.google.common.collect
    ImmutableMap)))

(def ^java.util.Map fake-headers
  (ImmutableMap/of
   "Authorization"
   (java.util.Arrays/asList
    (into-array String ["Bearer owner"]))))

(defn -getAuthenticationType ^String [this]
  (throw (ex-info "Not supported" {})))

(defn -getRequestMetadata
  ^java.util.Map
  [this ^java.net.URI uri]
  fake-headers)

(defn -hasRequestMetadata
  [this]
  true)

(defn -hasRequestMetadataOnly
  [this]
  true)

(defn -refresh
  [this])

(comment
  (import 'clj.firebase.google.FakeCredentials)
   )
