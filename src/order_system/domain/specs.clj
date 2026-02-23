(ns order-system.domain.specs
  "Especificaciones para validación de entrada"
  (:require [clojure.spec.alpha :as s]))

(s/def ::order-id uuid?)
(s/def ::customer-id string?)
(s/def ::items (s/coll-of (s/keys :req-un [::product-id ::quantity ::unit-price]
                                 :opt-un [::discount])))
(s/def ::product-id string?)
(s/def ::quantity int?)
(s/def ::unit-price number?)
(s/def ::discount (s/nilable number?))
(s/def ::total (s/nilable number?))
(s/def ::status #{:pending :confirmed :processing :completed :cancelled})
(s/def ::created-at inst?)

(s/def ::order-request
  (s/keys :req-un [::customer-id ::items]
          :opt-un [::order-id]))

(s/def ::order
  (s/keys :req-un [::order-id ::customer-id ::items ::status ::created-at]
          :opt-un [::total]))

(s/def ::order-event
  (s/keys :req-un [::order-id ::event-type ::payload ::timestamp]
          :opt-un [::correlation-id]))

(s/def ::event-type #{:order-created :order-updated :order-cancelled})

(s/def ::payload map?)
(s/def ::timestamp inst?)
(s/def ::correlation-id string?)

(defn validate-order [order]
  (s/valid? ::order-request order))

(defn explain-order [order]
  (s/explain-data ::order-request order))
