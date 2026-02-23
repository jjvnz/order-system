(ns order-system.domain.order
  "Lógica de dominio pura - sin dependencias de infraestructura"
  (:require [order-system.domain.specs :as specs])
  (:import (java.time Instant)))

(defn calculate-total [items]
  (reduce (fn [total {:keys [quantity unit-price discount]}]
            (let [line-total (* quantity unit-price)]
              (+ total (if discount
                        (- line-total discount)
                        line-total))))
          0
          items))

(defn create-order [customer-id items]
  (let [order {:order-id (java.util.UUID/randomUUID)
               :customer-id customer-id
               :items items
               :total (calculate-total items)
               :status :pending
               :created-at (Instant/now)}]
    (if (specs/validate-order order)
      order
      (throw (ex-info "Invalid order data" {:errors (specs/explain-order order)})))))

(defn confirm-order [order]
  (assoc order :status :confirmed))

(defn cancel-order [order]
  (assoc order :status :cancelled))

(defn create-order-event [order event-type]
  {:order-id (:order-id order)
   :event-type event-type
   :payload order
   :timestamp (Instant/now)})

(defmulti process-order-event
  (fn [order event-type]
    event-type))

(defmethod process-order-event :order-created [order _]
  (confirm-order order))

(defmethod process-order-event :order-cancelled [order _]
  (cancel-order order))

(defmethod process-order-event :default [order _]
  order)
