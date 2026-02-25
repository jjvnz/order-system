(ns order-system.adapters.postgres
  "Adapter para PostgreSQL - implementa OrderRepository"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [order-system.domain.ports :as ports]
            [clojure.data.json :as json])
  (:import (java.time Instant)
           (java.sql Timestamp)
           (java.util UUID)
           (org.postgresql.util PGobject)))

(defn- pgobject->str [val]
  (cond
    (nil? val) nil
    (string? val) val
    (instance? PGobject val) (.getValue ^PGobject val)
    :else (str val)))

(def ^:private insert-sql
  "INSERT INTO orders (order_id, customer_id, items, total, status, created_at)
   VALUES (?, ?, ?::jsonb, ?, ?, ?)
   ON CONFLICT (order_id) DO UPDATE SET
     status = EXCLUDED.status,
     updated_at = CURRENT_TIMESTAMP")

(def ^:private select-by-id-sql
  "SELECT order_id, customer_id, items, total, status, created_at, updated_at FROM orders WHERE order_id = ?")

(def ^:private select-all-sql
  "SELECT order_id, customer_id, items, total, status, created_at, updated_at FROM orders ORDER BY created_at DESC")

(defn- row->order [row]
  (-> row
      (update :items #(when % (json/read-str (pgobject->str %) :key-fn keyword)))
      (update :status #(when % (keyword (pgobject->str %))))
      (update :order-id #(if (string? %) (UUID/fromString %) %))
      (update :created-at #(when % (Instant/parse (pgobject->str %))))
      (update :updated-at #(when % (Instant/parse (pgobject->str %))))))

(defrecord PostgresOrderRepository [datasource]
  ports/OrderRepository

  (save-order [this order]
    (let [order-id (if (string? (:order-id order)) (UUID/fromString (:order-id order)) (:order-id order))
          customer-id (:customer-id order)
          items (json/write-str (:items order))
          total (bigdec (:total order))
          status (name (:status order))
          created-at (let [ts (:created-at order)]
                       (cond
                         (instance? Instant ts)
                         (Timestamp/from ts)

                         (string? ts)
                         (Timestamp/from (Instant/parse ts))

                         :else
                         (Timestamp/from (Instant/now))))]
      (try
        (jdbc/execute-one! datasource
                          [insert-sql order-id customer-id items total status created-at]
                          {:return-generated-keys false})
        (catch Exception e
          (println "[Postgres] Error saving order:" (.getMessage e))
          (throw e))))
      order)

  (find-order [this order-id]
    (let [order-id-str (if (string? order-id) order-id (str order-id))]
      (try
        (some-> (jdbc/execute-one! datasource
                                  [select-by-id-sql order-id-str]
                                  {:builder-fn rs/as-unqualified-lower-maps})
                row->order)
        (catch Exception e
          (println "[Postgres] Error finding order:" (.getMessage e))
          nil))))

  (list-orders [this]
    (try
      (mapv row->order
            (jdbc/execute! datasource
                          [select-all-sql]
                          {:builder-fn rs/as-unqualified-lower-maps}))
      (catch Exception e
        (println "[Postgres] Error listing orders:" (.getMessage e))
        []))))

(defn create-repository [datasource]
  (PostgresOrderRepository. datasource))

(defn init-schema! [datasource]
  (try
    (jdbc/execute! datasource
                  ["CREATE TABLE IF NOT EXISTS orders
                    (order_id VARCHAR(36) PRIMARY KEY,
                     customer_id VARCHAR(255) NOT NULL,
                     items JSONB NOT NULL,
                     total DECIMAL(10,2) NOT NULL,
                     status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'confirmed', 'processing', 'completed', 'cancelled')),
                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"])
    (jdbc/execute! datasource
                  ["CREATE INDEX IF NOT EXISTS idx_customer_id ON orders(customer_id)"])
    (jdbc/execute! datasource
                  ["CREATE INDEX IF NOT EXISTS idx_status ON orders(status)"])
    (jdbc/execute! datasource
                  ["CREATE INDEX IF NOT EXISTS idx_created_at ON orders(created_at)"])
    (println "[Postgres] Schema initialized successfully")
    (catch Exception e
      (println "[Postgres] Schema initialization error:" (.getMessage e))
      (throw e))))
