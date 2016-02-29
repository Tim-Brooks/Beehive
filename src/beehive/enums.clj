(ns beehive.enums
  (:import (beehive.enums EnumBuilder)
           (java.util.concurrent ConcurrentHashMap)))

(set! *warn-on-reflection* true)

(defn- enum-string [k]
  (.toUpperCase (.replace (name k) \- \_)))

(defn- result-enum-string [k s?]
  (str (enum-string k) (when-not s? "_F")))

(defn- key->enum-symbol [ks]
  (into {} (mapv (fn [k] [k (symbol (enum-string k))]) ks)))

(defn generate-rejected-enum [rejected-keys]
  (let [key->enum-string (into {} (map (fn [k]
                                         [k (symbol (enum-string k))])
                                       rejected-keys))
        cpath (EnumBuilder/buildRejectedEnum (map str (vals key->enum-string)))
        cpath (symbol cpath)]
    {:cpath cpath
     :key->enum-string key->enum-string}))

(defn generate-result-enum [result->success?]
  (let [key->enum-string (into {} (map (fn [[k s?]]
                                         [k (symbol (result-enum-string k s?))])
                                       result->success?))
        cpath (EnumBuilder/buildResultEnum (map str (vals key->enum-string)))
        cpath (symbol cpath)]
    {:cpath cpath
     :key->enum-string key->enum-string}))
