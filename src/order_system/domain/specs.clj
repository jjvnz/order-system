(ns order-system.domain.specs
  "Especificaciones para validación de entrada"
  (:require [clojure.spec.alpha :as s]))

(defn- valid-item? [item]
  (let [quantity (:quantity item 0)
        unit-price (:unit-price item 0)
        discount (:discount item 0)
        line-total (* quantity unit-price)]
    (or (nil? discount)
        (<= discount line-total))))

(s/def ::order-id uuid?)
(s/def ::customer-id (s/and string? not-empty #(<= (count %) 255)))
(s/def ::items (s/and
                (s/coll-of (s/keys :req-un [::product-id ::quantity ::unit-price]
                                   :opt-un [::discount])
                           :min-count 1)
                #(every? valid-item? %)))
(s/def ::product-id (s/and string? not-empty #(<= (count %) 100)))
(s/def ::quantity (s/and int? #(> % 0)))
(s/def ::unit-price (s/and number? #(> % 0)))
(s/def ::discount (s/and number? #(>= % 0) #(< % 10000)))
(s/def ::total (s/nilable number?))
(s/def ::status #{:pending :confirmed :processing :completed :cancelled})
(s/def ::created-at inst?)
(s/def ::updated-at inst?)

(s/def ::order-request
  (s/keys :req-un [::customer-id ::items]
          :opt-un [::order-id]))

(s/def ::order
  (s/keys :req-un [::order-id ::customer-id ::items ::status ::created-at]
          :opt-un [::total ::updated-at]))

(s/def ::order-event
  (s/keys :req-un [::order-id ::event-type ::payload ::timestamp]
          :opt-un [::correlation-id]))

(s/def ::event-type #{:order-created :order-updated :order-cancelled})

(s/def ::payload map?)
(s/def ::timestamp inst?)
(s/def ::correlation-id string?)

(defn validate-order [order]
  (if (s/valid? ::order-request order)
    [true nil]
    [false (s/explain-data ::order-request order)]))

(defn explain-order [order]
  (s/explain-data ::order-request order))

(defn validate-order-item [item]
  (s/valid? (s/keys :req-un [::product-id ::quantity ::unit-price] :opt-un [::discount]) item))

(defn validate-items [items]
  (and (seq items)
       (every? validate-order-item items)))
