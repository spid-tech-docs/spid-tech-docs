(ns spid-docs.import-endpoints-test
  (:require [midje.sweet :refer :all]
            [spid-docs.cultivate.content-shells :as cs]
            [spid-docs.import-endpoints :refer :all]))

(fact
 "Equal lists are equal. No changes detected."

 (compare-endpoint-lists [] []) => {:no-changes? true}
 (compare-endpoint-lists [(cs/endpoint)] [(cs/endpoint)]) => {:no-changes? true})

(fact
 "The properties that describe each endpoint (the schema) are checked for
  changes."

 (-> (compare-endpoint-lists [(-> (cs/endpoint)
                                  (dissoc :url)
                                  (assoc :urls []))]
                             [(cs/endpoint)])
     (dissoc :changed)) ;; not relevant for this test
 => {:schema-change? true
     :schema {:added ["url"]
              :removed ["urls"]}})

(fact
 "We also check the properties used to describe httpMethods."

 (-> (compare-endpoint-lists [(cs/endpoint
                               {:httpMethods {:GET (-> (cs/http-method)
                                                       (dissoc :name)
                                                       (assoc :id "x"))}})]
                             [(cs/endpoint
                               {:httpMethods {:GET (cs/http-method)}})])
     (dissoc :changed)) ;; not relevant for this test
 => {:schema-change? true
     :schema {:removed ["httpMethods/id"]
              :added ["httpMethods/name"]}})

(fact
 "The new endpoints are validated, to see if the changes are breaking,
  ie. no longer matches our expectations."

 (-> (compare-endpoint-lists [(cs/endpoint)]
                             [(-> (cs/endpoint)
                                  (dissoc :url)
                                  (assoc :urls []))])
     (dissoc :changed)) ;; not relevant for this test
 => {:schema-change? true
     :schema {:removed ["url"]
              :added ["urls"]}
     :breaking-change? true})

(fact
 "We detect added and removed endpoints, determined by [path method] pairs."

 (compare-endpoint-lists [(cs/endpoint
                           {:path "/path"
                            :httpMethods {:GET (cs/http-method)}})]
                         [(cs/endpoint
                           {:path "/path"
                            :httpMethods {:POST (cs/http-method)}})])
 => {:added [{:path "/path", :method :POST}]
     :removed [{:path "/path", :method :GET}]})

(fact
 "We also detect any changes to existing endpoints, but don't report on
  exactly what these changes are."

 (compare-endpoint-lists [(cs/endpoint
                           {:path "/path"
                            :httpMethods {:GET (cs/http-method
                                                {:name "abc"})}})]
                         [(cs/endpoint
                           {:path "/path"
                            :httpMethods {:GET (cs/http-method
                                                {:name "def"})}})])
 => {:changed [{:path "/path", :method :GET}]})