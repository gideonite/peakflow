(ns peakflow.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [noir.util.crypt :refer [encrypt]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.response :refer [file-response redirect response]]))

(defprotocol IDatabase
  (authorize [this username password] [this user-id])
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
  [users peakflows]
  IDatabase
  (authorize [this username password]
    (if-let [user (get-datum users username)]
      (if (= (user :salty-password)
             (encrypt (user :salt) password))
        (:salty-username user))
      nil))
  (authorize [this user-id]
    (get-datum users user-id)))

;; {:username :salty-password :salt :salty-username}
(def users (KVFileStore. "db/users"))
(def peakflows (KVFileStore. "db/peakflows"))
(def db (FileDB. users peakflows))

(defn handler
  [request]
  (let [authorized? (get-datum users ((request :session) :user-id))]
    (if authorized?
      (response "You must be a friend.")
      (response "Get outa here."))))

(defroutes app-routes
  (GET "/" [] (file-response "resources/public/landing.html"))
  (GET "/foobar" {session :session}
       (if (get-datum users (session :user-id))
         (-> (response "You must be a friend.")
           (assoc :session session))
         (response "Get outa here.")))
  (GET "/signup" [] (file-response "resources/public/signup.html"))
  (POST "/signup" [username]
        (response "NICE TRY BUCKO!"))
  (POST "/auth" [username password]
        (if-let [user-id (authorize db username password)]
          (-> (redirect "/foobar")
            (assoc :session {:user-id user-id}))
          "You don't exist. Sorry."))
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes [{:session handler}]))
