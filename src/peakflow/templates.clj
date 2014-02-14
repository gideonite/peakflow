(ns peakflow.templates
  (:require [net.cgrand.enlive-html :refer :all]))

(defn render [forms] (apply str forms))

(deftemplate navbar "templates/navbar.html" [])

(deftemplate landing "templates/landing.html" []
  [:.navbar] (content (navbar)))

(def rendered-landing (render (landing)))
