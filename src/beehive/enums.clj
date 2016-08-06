(ns beehive.enums
  (:import (beehive.java EnumBuilder ToCLJ EmptyEnum)))

(set! *warn-on-reflection* true)

(defn result-assertions [ks]
  (doseq [k ks]
    (assert (not (.contains (name k) "$FAILURE$"))
            "Result keyword cannot contain the string \"$FAILURE$\".")))

(defn enum-assertions [ks]
  (doseq [k ks]
    (assert (not (.contains (name k) "$DASH$"))
            "Enum keyword cannot contain the string \"$DASH$\".")))

(defn- enum-string [k]
  (.replace (name k) "-" "$DASH$"))

(defn- result-enum-string [k s?]
  (str (enum-string k) (when-not s? "$FAILURE$")))

(defn generate-rejected-class [rejected-keys]
  (enum-assertions rejected-keys)
  (let [key->enum-string (into {} (map (fn [k]
                                         [k (enum-string k)])
                                       rejected-keys))
        cpath (EnumBuilder/buildRejectedEnum (or (vals key->enum-string) []))]
    (resolve (symbol cpath))))

(defn generate-rejected-enum [rejected-keys]
  (enum-assertions rejected-keys)
  (let [key->enum-string (into {} (map (fn [k]
                                         [k (enum-string k)])
                                       rejected-keys))
        cpath (EnumBuilder/buildRejectedEnum (or (vals key->enum-string) []))
        cpath (symbol cpath)]
    {:cpath cpath
     :key->enum-string key->enum-string}))

(defn generate-result-class [result->success?]
  (if (empty? result->success?)
    EmptyEnum
    (do
      (let [ks (keys result->success?)]
        (result-assertions ks)
        (enum-assertions ks))
      (let [key->enum-string (map (fn [[k s?]] (result-enum-string k s?))
                                  result->success?)
            cpath (EnumBuilder/buildResultEnum (or key->enum-string []))]
        (resolve (symbol cpath))))))

(defn generate-result-enum [result->success?]
  (let [ks (keys result->success?)]
    (result-assertions ks)
    (enum-assertions ks))
  (let [key->enum-string (into {} (map (fn [[k s?]]
                                         [k (result-enum-string k s?)])
                                       result->success?))
        cpath (EnumBuilder/buildResultEnum (or (vals key->enum-string) []))
        cpath (symbol cpath)]
    {:cpath cpath
     :key->enum-string key->enum-string}))

(defn enum-form [cpath string]
  `(. ~cpath ~(symbol string)))

(defmacro create-type-map [{:keys [key->enum-string cpath]}]
  `(do
     (-> {}
         ~@(map (fn [[k es]]
                  (list assoc k `(. ~cpath ~(symbol es))))
                key->enum-string))))

(defmacro rejected-keys->enum [rejected-keys]
  (let [enum-map (generate-rejected-enum rejected-keys)]
    `(create-type-map ~enum-map)))

(defmacro result-keys->enum [result->success?]
  (let [enum-map (generate-result-enum result->success?)]
    `(create-type-map ~enum-map)))

;; Convertors

(defn keyword->enum [type keyword]
  (first (filter #(identical? keyword (.keyword ^ToCLJ %))
                 (.getEnumConstants ^Class type))))

(defn enum->keyword [enum]
  (when enum (.keyword ^ToCLJ enum)))

(defn enum->keyword-map [^Class enum]
  (into {} (map (fn [^ToCLJ e] [(.keyword e) e]) (.getEnumConstants enum))))

(defn enum-class-to-key->form [^Class enum-class]
  (into {} (map (fn [^Enum k]
                  [(enum->keyword k) (enum-form enum-class (.name k))])
                (.getEnumConstants enum-class))))