(ns order-system.adapters.postgres
  "Adapter para PostgreSQL - implementa OrderRepository"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [order-system.domain.ports :as ports]
            [clojure.data.json :as json])
  (:import (java.util UUID)))

(defrecord PostgresOrderRepository [datasource]
  ports/OrderRepository

  (save-order [this order]
    (jdbc/execute-one! datasource
                      ["INSERT INTO orders (order_id, customer_id, items, total, status, created_at)
                        VALUES (?, ?, ?::jsonb, ?, ?, ?)"
                       (str (:order-id order))
                       (:customer-id order)
                       (json/write-str (:items order))
                       (:total order)
                       (name (:status order))
                       (:created-at order)])
    order)

  (find-order [this order-id]
    (some-> (jdbc/execute-one! datasource
                              ["SELECT order_id, customer_id, items, total, status, created_at FROM orders WHERE order_id = ?"
                               (str order-id)]
                              {:builder-fn rs/as-unqualified-lower-maps})
            (update :items #(when % (json/read-str % :key-fn keyword)))
            (update :status keyword)
            (update :order-id UUID)))

  (list-orders [this]
    (mapv (fn [row]
            (-> row
                (update :items #(when % (json/read-str % :key-fn keyword)))
                (update :status keyword)
                (update :order-id UUID)))
          (jdbc/execute! datasource
                        ["SELECT order_id, customer_id, items, total, status, created_at FROM orders ORDER BY created_at DESC"]
                        {:builder-fn rs/as-unqualified-lower-maps}))))

(defn create-repository [datasource]
  (PostgresOrderRepository. datasource))

(defn init-schema! [datasource]
  (jdbc/execute! datasource
                ["CREATE TABLE IF NOT EXISTS orders
                  (order_id VARCHAR(36) PRIMARY KEY,
                   customer_id VARCHAR(255) NOT NULL,
                   items JSONB NOT NULL,
                   total DECIMAL(10,2) NOT NULL,
                   status VARCHAR(20) NOT NULL,
                   created_at TIMESTAMP NOT NULL)"])
  (jdbc/execute! datasource
                ["CREATE INDEX IF NOT EXISTS idx_customer_id ON orders(customer_id)"])
  (jdbc/execute! datasource
                ["CREATE INDEX IF NOT EXISTS idx_status ON orders(status)"]))
