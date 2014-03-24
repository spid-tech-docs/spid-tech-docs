(ns spid-docs.sample-responses
  "Tools to generate sample responses for all documentet endpoints. Actually uses
   the API to ensure that sample responses reflect the API, and then runs the
   result through a series of functions that anonymizes and scrambles
   potentially sensitive data."
  (:require [clojure.data.json :as json]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as str]
            [spid-docs.api-client :as api]
            [spid-docs.formatting :refer [to-id-str]]
            [spid-docs.homeless :refer [update-existing]])
  (:import java.util.Date))

(def rand-digit (partial rand-int 10))

(def scramble-numbers #(apply str (repeatedly (count %) rand-digit)))

(defn mask-address
  "Anonymize addresses "
  [num address]
  (assoc address
    :streetNumber (str (inc num))
    :postalCode "0123"
    :streetAddress "STREET"
    :formatted (str "STREET " (inc num) ", 0123 OSLO, NORGE")))

(defn mask-addresses [addresses]
  (->> addresses
       (map-indexed #(vector (first %2)
                             (mask-address %1 (second %2))))
       (into {})))

(defn mask-sensitive-data [data]
  (update-existing
   data
   [:clientId] "[Your client ID]"
   [:merchantId] "[Your merchant ID]"
   [:userId] scramble-numbers
   [:email] "user@domain.tld"
   [:ip] "127.0.0.1"
   [:emails] (fn [emails]
               (map-indexed #(assoc %2 :value (str "user@domain" (inc %1) ".tld")) emails))
   [:addresses] mask-addresses))

(defn process-data [data]
  (if (map? data)
    (mask-sensitive-data data)
    (->> data
         (take 1)
         (map process-data))))

(defn process-sample-response [response]
  (with-out-str (json/pprint (->> response :data process-data) :escape-slash false)))

(def target-directory "resources/sample-responses")

(defn- get-filename-stub [endpoint]
  (.toLowerCase (str target-directory "/"
                     (to-id-str (:path endpoint)) "-"
                     (name (:method endpoint)))))

(defn- create-sample-responses [endpoint response]
  (let [filename-stub (get-filename-stub endpoint)
        sample (process-sample-response response)]
    [{:filename (str filename-stub ".json"),  :contents sample}
     {:filename (str filename-stub ".jsonp"), :contents (str "callback(" (str/trim sample) ");\n")}]))

(defn- json-parse-data [response]
  (assoc response :data (:data (json/read-json (:data response)))))

(defn- ensure-get [endpoint]
  (if (not (= (:method endpoint) :GET))
    (throw (Exception. (str (name (:method endpoint)) " user sample response not implemented")))))

(defn- demo-user-sample [endpoint]
  (ensure-get endpoint)
  (-> (api/get-config)
      (api/get-login-token)
      (api/user-get (:path endpoint))
      (rename-keys {:body :data
                    :status :code})
      (json-parse-data)))

(defn- save-sample-responses [samples]
  (doseq [{:keys [filename contents]} samples]
    (spit filename contents)))

(defmulti generate-sample-response #(vector (:method %) (:path %)))

(defmethod generate-sample-response [:GET "/me"] [endpoint]
  (save-sample-responses (create-sample-responses endpoint (demo-user-sample endpoint))))

(defmethod generate-sample-response :default [endpoint]
  (ensure-get endpoint)
  (save-sample-responses (create-sample-responses endpoint (api/GET (:path endpoint)))))
