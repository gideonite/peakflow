(ns peakflow.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.session :as session]
            [ring.util.response :as response]))

(defprotocol IDatabase
  (authorized? [this store username password])
  (save-user! [this username password])
  (delete-user! [this username])
  (datum! [this username peakflow])
  (user-data [this store username]))

(defprotocol IKVStore
  (assoc-datum! [this key value] "Adds the record to the store.")
  (get-datum [this key] "Gets a record based on the key."))

(defn slurp-edn
  [filename]
  (let [data (slurp filename)]
    (if (= "" data)
      {}
      (read-string data))))

(deftype KVFileStore
  [filename]
  IKVStore
  (assoc-datum! [this key value]
    (let [data (slurp-edn filename)]
      (spit filename
            (assoc data key value))))
  (get-datum [this key]
    (let [data (slurp-edn filename)]
      (data key))))

(comment
  (get-datum (UserFileStore. "db/users.edn") :Gideon)
  (assoc-datum! (UserFileStore. "db/users.edn") :Gideon {:user :NOTGIDEON :pswd :is :attr :cool}))

(def users (KVFileStore. "db/users.edn"))
(def peakflows (KVFileStore. "db/peakflows.edn"))

(deftype FileDB
  [users-kvstore peakflows-kvstore]
  IDatabase)

(defroutes app-routes
  (GET "/" [] (response/file-response "resources/public/landing.html"))
  (GET "/foobar" [] "FOOBAR")
  (POST "/auth" [username password] (str "POSTed" username password))
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
