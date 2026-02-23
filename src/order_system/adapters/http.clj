(ns order-system.adapters.http
  "Adapter HTTP con reitit y ring-jetty-adapter"
  (:require [ring.adapter.jetty :as jetty]
            [muuntaja.core :as muuntaja]
            [muuntaja.middleware :as muuntaja-middleware]
            [reitit.ring :as ring]
            [order-system.domain.ports :as ports]
            [order-system.domain.order :as domain]
            [order-system.domain.specs :as specs]
            [order-system.adapters.kafka :as kafka]))

(def ^:dynamic *publisher* nil)

(defn handle-create-order [request]
  (let [body (:body request)
        validated (specs/validate-order body)]
    (if validated
      (try
        (let [order (domain/create-order (:customer-id body) (:items body))
              event (domain/create-order-event order :order-created)]
          (ports/publish-event *publisher* kafka/order-events-topic event)
          {:status 202
           :body {:order-id (str (:order-id order))
                  :status "accepted"
                  :message "Order queued for processing"}})
        (catch Exception e
          {:status 500
           :body {:error (.getMessage e)}}))
      {:status 400
       :body {:errors (specs/explain-order body)}})))

(defn handle-get-order [request]
  (let [order-id (get-in request [:parameters :path :order-id])
        repository (:order-repository request)]
    (if-let [order (ports/find-order repository (java.util.UUID/fromString order-id))]
      {:status 200 :body order}
      {:status 404 :body {:error "Order not found"}})))

(defn handle-list-orders [request]
  (let [repository (:order-repository request)]
    {:status 200 :body (ports/list-orders repository)}))

(def routes
  ["/api/orders"
   {:post handle-create-order
    :get handle-list-orders
    :parameters {:path {:order-id string?}}}
   ["/:order-id"
    {:get handle-get-order}]])

(def muuntaja-instance
  (muuntaja/create
   (muuntaja/default-options
    {:return :bigdec
     :encode-key-fn true})))

(def app
  (ring/ring-handler
   (ring/router
    [routes]
    {:data {:middleware [muuntaja-middleware/wrap-format]
            :muuntaja muuntaja-instance}})
   (constantly {:status 404 :body "Not found"})))

(defn start-server [publisher repository port]
  (jetty/run-jetty
   (fn [request]
     (binding [*publisher* publisher]
       (app (assoc request :order-repository repository))))
   {:port port :join? false}))
