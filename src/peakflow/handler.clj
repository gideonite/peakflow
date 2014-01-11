(ns peakflow.handler
  (:require [peakflow.database :refer :all]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [file-response redirect response]]))

(defn authorize-session
  [session]
  (authorize db (session :user-id)))

(defn wrap-session
  [response session]
  (-> response (assoc :session session)))

(defroutes app-routes
  (GET "/" [] (file-response "resources/public/landing.html"))
  (GET "/home" {session :session}
       (if (authorize-session session)
         (wrap-session (file-response "resources/public/home.html") session)
         (response "Get outa here.")))
  (POST "/home/data" {session :session params :params}
        (if-let [user (authorize-session session)]
          (wrap-json-response (fn [req] (wrap-session
                                          (response (save-peakflow! db user (select-keys params [:timestamp :peakflow])))
                                          session)))
          (response (str "Get outa here. You are not authorized for this action."))))
  (GET "/home/data" {session :session params :params}
        (if-let [user (authorize-session session)]
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
  "$2a$10$hsHJUanslb0LVSc3kRL.8OXXfVSfooC4PqG0ICZA/HN/0nzKGTxjG")
