(ns peakflow.database (:require [clojure.pprint :refer [pprint]]
            [noir.util.crypt :refer [encrypt gen-salt]]))

;;
;; FILE STORE
;;

(defn slurp-edn
  "filename (string) -> Clojure data structure."
  [filename]
  (let [data (slurp filename)]
    (if (= "" data)
      {}
      (read-string data))))

(defn spit-edn
  "filename  data -> nil.
  Puts the data into the file, overwriting anything in the file."
  [filename data]
  (spit filename
        (with-out-str (pprint data))))

(defprotocol IStoreKV
  (put-value [this k v])
  (spit-data [this data])
  (get-value [this k]))

(defrecord FileStore [filename])

(defn file-store []
  (->FileStore "db/db.edn"))

(extend-type FileStore
  IStoreKV
  (get-value [this k]
             (get (slurp-edn (:filename this)) k))
  (spit-data [this data] (spit-edn (:filename this) data))
  (put-value [this k v] (spit-edn (:filename this)
                                (assoc (slurp-edn (:filename this)) k v))))

(comment
  (def store (file-store))
  (get-value store :foo)
  (put-value store :foo :bar)
  (get-value store :foo))

(defrecord CachedFileStore [filestore !cache])

(defn cached-file-store []
  (->CachedFileStore (file-store) (atom {})))

#_(cached-file-store)

(extend-type CachedFileStore
  IStoreKV
  (get-value [this k]
         (if-let [value (get @(:!cache this) k)]
           value
           (if-let [file-value (get-value (:filestore this) k)]
             file-value
             nil)))
  (put-value [this k v]
         (swap! (:!cache this) assoc k v)
         (spit-data (:filestore this) @(:!cache this))))

(comment
  (def store (cached-file-store))

  (get-value store :foo)

  (put-value store :foo :bar)
  (get-value store :foo)

  (put-value store :foo :baz)
  (get-value store :foo))

(defrecord FileDB [store])

(defn file-db []
  (->FileDB (cached-file-store)))

;;
;; DATABASE INTERFACE AND IMPL
;;

(defprotocol IDatabase
  (lookup-user-db [this userid])
  (authorize-db [this username password])
  (save-user!-db [this user])
  (delete-user!-db [this user])
  (save-peakflow!-db [this userid peakflow])
  (get-peakflows-db [this userid]))

(extend-type FileDB
  IDatabase
  (lookup-user-db [this userid] (get-value (:store this) userid))
  (save-user!-db [this user]
              (if-not (lookup-user-db this (:username user))
                (do (put-value (:store this) (:username user) user)
                    (put-value (:store this) (:encrypted-username user) user)
                  true)
                false))
  (delete-user!-db [this user]
                (put-value (:store this) (:username user) nil)
                (put-value (:store this) (:encrypted-username user) nil))
  (authorize-db [this username password]
             (if-let [user (lookup-user-db this username)]
               (= (:password user)
                  (encrypt (:pass-salt user) password))
               false))
  (save-peakflow!-db [this userid peakflow]
                  (if-let [user (lookup-user-db this userid)]
                    (do
                      (put-value (:store this)
                                 (:username user)
                                 (assoc user :peakflows
                                   (conj (get user :peakflows [])
                                         peakflow))) true)
                    false))
  (get-peakflows-db [this userid]
                 (if-let [user (lookup-user-db this userid)]
                   (:peakflows user))))

;;
;; USER AND PEAKFLOW RECORD TYPES
;;

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

(defrecord Peakflow [timestamp value])
(defn create-peakflow [timestamp value] (->Peakflow timestamp value))

;;
;; API
;;

;; The "API" merely factors out the database reference. Presumably,
;; if you are going to change it in one place, you are going to want
;; to change it everywhere.

(def db (file-db))

(defn lookup-user
  [userid]
  (lookup-user-db db userid))

(defn authorize
  [username password]
  (authorize-db db username password))

(defn save-user!
  "User -> Boolean.
  Returns true if the user was successfully saved.
  Fails to save if the user already exists."
  [User]
  (save-user!-db db User))

(defn save-peakflow!
  "userid, Peakflow -> Boolean."
  [userid Peakflow]
  (save-peakflow!-db db userid Peakflow))

(defn get-peakflows
  [userid]
  (get-peakflows-db db userid))

(comment
  (def db (file-db))
  (def me (create-user "Gideon" "foobar"))
  (save-user!-db db me)
  (lookup-user-db db (:encrypted-username (lookup-user-db db "Gideon")))
  (authorize-db db "Gideon" "foobar")
  (save-peakflow!-db db "Gideon" :peakflow1)
  (save-peakflow!-db db "Gideon" :peakflow2)
  (save-peakflow!-db db me :peakflow3)
  (delete-user!-db db me)

  (save-user (create-user "foobaz" "elephant"))
  (:username (lookup-user "foobaz"))
  (create-peakflow "11:00AM" 660)
  (save-peakflow! "foobaz" (create-peakflow "11:00AM" 660))
  (get-peakflows "foobaz"))