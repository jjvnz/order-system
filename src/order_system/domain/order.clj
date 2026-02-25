(ns order-system.domain.order
  "Lógica de dominio pura - sin dependencias de infraestructura"
  (:import (java.util UUID)))

(defn calculate-line-total [{:keys [quantity unit-price discount]}]
  (let [line-total (* quantity unit-price)]
    (if (and discount (pos? discount))
      (max 0 (- line-total discount))
      line-total)))

(defn calculate-total [items]
  (if (empty? items)
    0
    (reduce (fn [total item]
              (+ total (calculate-line-total item)))
            0
            items)))

(defn validate-items [items]
  (when (seq items)
    (every? (fn [{:keys [quantity unit-price discount] :as item}]
              (and (some? quantity)
                   (some? unit-price)
                   (pos? quantity)
                   (pos? unit-price)
                   (or (nil? discount)
                       (and (number? discount)
                            (>= discount 0)
                            (<= discount (* quantity unit-price))))))
            items)))

(defn create-order [customer-id items]
  (when (or (nil? customer-id) (empty? customer-id))
    (throw (ex-info "Customer ID is required" {:type :validation-error})))
  (when-not (validate-items items)
    (throw (ex-info "Invalid items in order" {:type :validation-error})))
  (let [order-id (UUID/randomUUID)
        created-at (java.time.Instant/now)
        total (calculate-total items)]
    {:order-id order-id
     :customer-id customer-id
     :items items
     :total total
     :status :pending
     :created-at created-at}))

(defn confirm-order [order]
  (assoc order :status :confirmed))

(defn cancel-order [order]
  (when (= :cancelled (:status order))
    (throw (ex-info "Order already cancelled" {:type :invalid-state :order-id (:order-id order)})))
  (assoc order :status :cancelled))

(defn complete-order [order]
  (when-not (#{:confirmed :processing} (:status order))
    (throw (ex-info "Order cannot be completed from current state" {:type :invalid-state :order-id (:order-id order)})))
  (assoc order :status :completed))

(defn process-order [order]
  (assoc order :status :processing))

(defn create-order-event [order event-type]
  {:order-id (:order-id order)
   :event-type event-type
   :payload order
    :timestamp (java.time.Instant/now)})

(defmulti process-order-event
  (fn [order event-type]
    event-type))

(defmethod process-order-event :order-created [order _]
  (confirm-order order))

(defmethod process-order-event :order-cancelled [order _]
  (cancel-order order))

(defmethod process-order-event :default [order _]
  order)
