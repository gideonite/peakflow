(ns peakflow.database
  (:require [clojure.pprint :refer [pprint]]
            [noir.util.crypt :refer [encrypt gen-salt]]))

(defprotocol IDatabase
  (authorize [this username password] [this user-id])
  (create-user! [this user password])
  (delete-user! [this user password])
  (save-peakflow! [this user peakflow])
  (user->data [this user]))

(defprotocol IKVStore
  (assoc-datum! [this key value] "Adds the record to the store.")
  (get-datum [this key] "Gets a record based on the key.")
  (dissoc-datum! [this key] "Removes the record associated with the key."))

(defn slurp-edn
  [filename]
  (let [data (slurp filename)]
    (if (= "" data)
      {}
      (read-string data))))

(defn spit-edn
  [filename data]
  (spit filename
        (with-out-str (pprint data))))

(deftype KVFileStore
  [filename]
  IKVStore
  (assoc-datum! [this key value]
    (let [data (slurp-edn filename)]
      (spit-edn filename
            (assoc data key value))))
  (get-datum [this key]
    (let [data (slurp-edn filename)]
      (get data key)))
  (dissoc-datum! [this key]
    (let [data (slurp-edn filename)]
      (spit-edn filename (dissoc data key)))))

(defrecord User [username password user-salt pass-salt encrypted-username])

(defn create-user [username password]
  "Returns a new user record given a username and password. By 'new' I mean
  that it creates new salts."
  (let [user-salt (gen-salt)
        pass-salt (gen-salt)
        encrypted-username (encrypt user-salt username)
        encrypted-password (encrypt pass-salt password)]
    (->User username
            encrypted-password
            user-salt
            pass-salt
            encrypted-username)))

(deftype FileDB
  [users peakflows]
  IDatabase
  (authorize [this username password]
    (if-let [user (get-datum users username)]
      (if (= (user :password)
             (encrypt (user :pass-salt) password))
        (:encrypted-username user))
      nil))
  (authorize [this user-id]
    (get-datum users user-id))
  (create-user! [this username password]
    (if (get-datum users username)
      (str "User '" username  "' exists")
      (let [user (create-user username password)]
        (assoc-datum! users (:encrypted-username user) user)
        (assoc-datum! users (:username user) user)
        user)))
  (delete-user! [this username password]
    (println (authorize this username password))
    (if-let [user-id (authorize this username password)]
      (let [user-record (get-datum users username)]
        (dissoc-datum! users (:encrypted-username user-record))
        (dissoc-datum! users username))
      "Sorry, you are unauthorized."))
  (save-peakflow! [this user peakflow]
    (let [username (user :username)
          data (get-datum peakflows username)]
      (assoc-datum! peakflows username (conj data peakflow))
      peakflow))
  (user->data [this user]
    (get-datum peakflows (user :username))))

(def users (KVFileStore. "db/users"))
(def peakflows (KVFileStore. "db/peakflows"))
(def db (FileDB. users peakflows))
