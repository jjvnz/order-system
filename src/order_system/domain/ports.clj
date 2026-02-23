(ns order-system.domain.ports
  "Protocolos que definen los puertos (interfaces) del sistema"
  (:require [clojure.spec.alpha :as s]))

(defprotocol OrderRepository
  (save-order [this order])
  (find-order [this order-id])
  (list-orders [this]))

(defprotocol MessagePublisher
  (publish-event [this topic event])
  (subscribe [this topic handler]))

(defprotocol EventConsumer
  (start-consuming [this]))
