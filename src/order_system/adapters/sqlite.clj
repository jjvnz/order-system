(ns order-system.adapters.sqlite
  "Adapter para SQLite - implementa OrderRepository"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [order-system.domain.ports :as ports]
            [clojure.java-time :as jt])
  (:import (java.time Instant)))

(extend-protocol rs/ReadableColumn
  Instant
  (read-column-by-label [^java.sql.ResultSet rs ^String label]
    (let [v (.getObject rs label)]
      (when v (jt/instant v))))
  (read-column-by-index [^java.sql.ResultSet rs ^Integer idx]
    (let [v (.getObject rs idx)]
      (when v (jt/instant v)))))

(defn ->order-row [order]
  {:order_id     (str (:order-id order))
   :customer_id (:customer-id order)
   :items       (pr-str (:items order))
   :total       (:total order)
   :status      (name (:status order))
   :created_at  (:created-at order)})

(defn row->order [row]
  {:order-id     (java.util.UUID/fromString (:order_id row))
   :customer-id  (:customer_id row)
   :items        (read-string (:items row))
   :total        (:total row)
   :status       (keyword (:status row))
   :created-at   (:created_at row)})

(defrecord SQLiteOrderRepository [datasource]
  ports/OrderRepository

  (save-order [this order]
    (jdbc/execute-one! datasource
                      ["INSERT INTO orders (order_id, customer_id, items, total, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)"
                       (:order-id order)
                       (:customer-id order)
                       (pr-str (:items order))
                       (:total order)
                       (name (:status order))
                       (:created-at order)]
                      {:return-keys true})
    order)

  (find-order [this order-id]
    (some-> (jdbc/execute-one! datasource
                              ["SELECT * FROM orders WHERE order_id = ?"
                               (str order-id)]
                              {:builder-fn rs/unqualified})
            row->order))

  (list-orders [this]
    (mapv row->order
          (jdbc/execute! datasource
                        ["SELECT * FROM orders ORDER BY created_at DESC"]
                        {:builder-fn rs/unqualified}))))

(defn create-repository [datasource]
  (SQLiteOrderRepository. datasource))

(defn init-schema! [datasource]
  (jdbc/execute! datasource
                ["CREATE TABLE IF NOT EXISTS orders
                  (order_id TEXT PRIMARY KEY,
                   customer_id TEXT NOT NULL,
                   items TEXT NOT NULL,
                   total REAL,
                   status TEXT NOT NULL,
                   created_at TIMESTAMP NOT NULL))"]))
