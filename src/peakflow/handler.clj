(ns peakflow.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [noir.util.crypt :refer [encrypt]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [file-response redirect response]]))

(defprotocol IDatabase
  (authorize [this username password] [this user-id])
  (save-user! [this user password])
  (delete-user! [this user])
  (save-peakflow! [this user peakflow])
  (user->data [this user]))

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
    (get-datum users user-id))
  (save-peakflow! [this user peakflow]
    (let [username (user :username)
          data (get-datum peakflows username)]
      (assoc-datum! peakflows username (conj data peakflow))
      peakflow))
  (user->data [this user]
    (get-datum peakflows (user :username))))

;; {:username :salty-password :salt :salty-username}
(def users (KVFileStore. "db/users"))
(def peakflows (KVFileStore. "db/peakflows"))
(def db (FileDB. users peakflows))

(defroutes app-routes
  (GET "/" [] (file-response "resources/public/landing.html"))
  (GET "/home" {session :session}
       (if (authorize db (session :user-id))
         (-> (file-response "resources/public/home.html")
           (assoc :session session))
         (response "Get outa here.")))
  (POST "/home/data" {session :session params :params}
        (if-let [user (authorize db (session :user-id))]
          (wrap-json-response (fn [req] (response (save-peakflow! db user (params :peakflow)))))
          (response (str "Get outa here. You are not authorized for this action."))))
  (GET "/home/data" {session :session params :params}
        (if-let [user (authorize db (session :user-id))]
          (wrap-json-response (fn [req] (response (user->data db user))))
          (response (str "Get outa here. You are not authorized for this action."))))
  (GET "/signup" [] (file-response "resources/public/signup.html"))
  (POST "/signup" [username]
        (response "NICE TRY BUCKO!"))
  (POST "/auth" [username password]
        (if-let [user-id (authorize db username password)]
          (-> (redirect "/home")
            (assoc :session {:user-id user-id}))
          "You don't exist. Sorry."))
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
