(ns peakflow.database (:require [clojure.pprint :refer [pprint]]
            [noir.util.crypt :refer [encrypt gen-salt]]))

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

(defprotocol IDatabase
  (lookup-user [this userid])
  (authorize [this username password])
  (save-user! [this user])
  (delete-user! [this user])
  (save-peakflow! [this user peakflow])
  (get-peakflows [this user]))

(extend-type FileDB
  IDatabase
  (lookup-user [this userid] (get-value (:store this) userid))
  (save-user! [this user]
              (if-not (lookup-user this (:username user))
                (do (put-value (:store this) (:username user) user)
                    (put-value (:store this) (:encrypted-username user) user)
                  true)
                false))
  (delete-user! [this user]
                (put-value (:store this) (:username user) nil)
                (put-value (:store this) (:encrypted-username user) nil))
  (authorize [this username password]
             (if-let [user (lookup-user this username)]
               (= (:password user)
                  (encrypt (:pass-salt user) password))
               false))
  (save-peakflow! [this userid peakflow]
                  (if-let [user (lookup-user this userid)]
                    (do
                      (put-value (:store this)
                               (:username user)
                               (assoc user :peakflows
                                 (conj (get user :peakflows [])
                                            peakflow))) true)
                    false))
  (get-peakflows [this userid]
                 (if-let [user (lookup-user this userid)]
                   (:peakflows user))))

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

(comment
  (def db (file-db))
  (def me (create-user "Gideon" "foobar"))
  (save-user! db me)
  (lookup-user db (:encrypted-username (lookup-user db "Gideon")))
  (authorize db "Gideon" "foobar")
  (save-peakflow! db "Gideon" :peakflow1)
  (save-peakflow! db "Gideon" :peakflow2)
  (save-peakflow! db me :peakflow3)
  (delete-user! db me))

(defrecord Peakflow [timestamp value])

(def db (file-db))

(defn save-user [user]
  (save-user! db user))

(save-user (create-user "asdf" "fdsa"))