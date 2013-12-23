(ns peakflow.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [noir.util.crypt :refer [encrypt gen-salt]]
            [ring.util.response :as response :refer [redirect]]))

(defprotocol IDatabase
  (authorize [this username password])
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
      (get data key))))

(comment
  (get-datum (UserFileStore. "db/users.edn") :Gideon)
  (assoc-datum! (UserFileStore. "db/users.edn") :Gideon {:user :NOTGIDEON :pswd :is :attr :cool}))

(comment
  (def salt "$2a$10$hsHJUanslb0LVSc3kRL.8OBVoYp/RHzvVPo9tSPyiHT/ZLYn0aXuS")

  ;; user-salted
  (encrypt salt "gideon@foo.bar")
  "$2a$10$hsHJUanslb0LVSc3kRL.8OJeyyO2sbRafkih.rewWVRzoG8oDptee"

  ;; password
  (encrypt salt "foobar")
  "$2a$10$hsHJUanslb0LVSc3kRL.8OXXfVSfooC4PqG0ICZA/HN/0nzKGTxjG"
  )

(deftype FileDB
  [users-kvstore peakflows-kvstore]
  IDatabase
  (authorize [this username password]
    (if-let [user (get-datum users-kvstore username)]
      (if (= (user :salty-password)
             (encrypt (user :salt) password))
        (:salty-username user))
      nil)))

;; {:username :salty-password :salt :user-id}
(def users (KVFileStore. "db/users"))

(def peakflows (KVFileStore. "db/peakflows"))
(def db (FileDB. users peakflows))

(defroutes app-routes
  (GET "/" [] (response/file-response "resources/public/landing.html"))
  (GET "/foobar" [] "FOOBAR")
  (GET "/signup" [] (response/file-response "resources/public/signup.html"))
  (POST "/signup" [username]
        "NICE TRY BUCKO!")
  (POST "/auth" [username password]
        (if-let [user-id (authorize db username password)]
          (-> (redirect "/foobar")
              (assoc :session {:session-id user-id}))
          "You don't exist. Sorry."))
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
