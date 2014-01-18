(ns peakflow.handler
  (:require [peakflow.database :refer [lookup-user authorize save-user! save-peakflow! get-peakflows create-peakflow create-user]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [file-response redirect response]]))

(defn authorize-session
  [session]
  ((comp not nil?) (lookup-user (session :id))))

(defn wrap-session
  [response session]
  (-> response (assoc :session session)))

(def not-authorized-res (response "You are not authorized...so piss off."))

(defroutes app-routes
  (GET "/" [] (file-response "resources/public/landing.html"))
  (GET "/login" [username password]
       (if (authorize username password)
         (let [id (:encrypted-username (lookup-user username))]
           (-> (redirect "/home")
               (assoc :session {:id id})))
         not-authorized-res))
  (GET "/home" {session :session}
       (if (authorize-session session)
         (wrap-session (file-response "resources/public/home.html") session)
         (response "Get outa here.")))
  (GET "/home/data" {session :session params :params}
       ;; Gets the peakflows associated with the id of the session.
       ;; Sends either those peakflows or [].
       (if (authorize-session session)
         (let [peakflows (or (get-peakflows (:id session)) [])]
           (wrap-json-response (fn [req] (response peakflows))))))
  (POST "/home/data" {session :session params :params}
        ;; Saves the peakflow and echoes it back over the wire.
        (if (authorize-session session)
          (let [user (lookup-user (session :user-id))
                peakflow (create-peakflow (select-keys params [:timestamp :peakflow]))]
            (save-peakflow! (:username user) peakflow)
            (wrap-json-response (fn [req] (wrap-session (response peakflow)
                                                        session))))
          not-authorized-res)))

;; (GET "/home/data" {session :session params :params}
;;         (if-let [user (authorize-session session)]
;;           (wrap-json-response (fn [req] (response (user->data db user))))
;;           (response (str "Get outa here. You are not authorized for this action."))))
;;   (GET "/signup" [] (file-response "resources/public/signup.html"))
;;   (POST "/signup" [username]
;;         (response "NICE TRY BUCKO!"))
;;   (POST "/new-user" [username password]
;;         (let [user-record (create-user! db username password)]
;;           (-> (redirect "/home")
;;             (assoc :session {:user-id (:encrypted-username user-record)}))))
;;   (POST "/auth" [username password]
;;         (if-let [user-id (authorize db username password)]
;;           (-> (redirect "/home")
;;             (assoc :session {:user-id user-id}))
;;           "You don't exist. Sorry."))
;;   (route/not-found "Not Found")

(def app
  (handler/site app-routes))