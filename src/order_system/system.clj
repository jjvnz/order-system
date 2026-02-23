(ns order-system.system
  "Composición del sistema con Integrant"
  (:gen-class)
  (:require [order-system.adapters.postgres :as postgres]
            [order-system.adapters.kafka :as kafka]
            [order-system.adapters.http :as http]
            [next.jdbc :as jdbc]))

(def config
  (let [db-url (or (System/getenv "DATABASE_URL")
                   "jdbc:postgresql://postgres:5432/orders_db?user=orders&password=orders_password")
        brokers (or (System/getenv "KAFKA_BROKERS") "kafka:29092")
        port (Long/parseLong (or (System/getenv "HTTP_PORT") "8081"))]
    {:database-url db-url
     :kafka {:brokers [brokers]}
     :http {:port port}}))

(def system-state (atom nil))

(defn init-db! []
  (let [db-url (:database-url config)
        parts (re-find #"postgresql://([^:]+):(\d+)/(\w+)\?user=(\w+)&password=(\w+)" db-url)]
    (println "Connecting to PostgreSQL...")
    (let [ds (if parts
               (jdbc/get-datasource {:dbtype "postgresql"
                                     :host (second parts)
                                     :port (Integer/parseInt (nth parts 2))
                                     :dbname (nth parts 3)
                                     :user (nth parts 4)
                                     :password (nth parts 5)})
               (jdbc/get-datasource {:dbtype "postgresql"
                                     :dbname "orders_db"
                                     :host "postgres"
                                     :port 5432
                                     :user "orders"
                                     :password "orders_password"}))]
      (postgres/init-schema! ds)
      (postgres/create-repository ds))))

(defn init-kafka! []
  (let [brokers (get-in config [:kafka :brokers])]
    (try
      (kafka/ensure-topic! brokers kafka/order-events-topic)
      (catch Exception e
        (println "Topic creation error:" (.getMessage e))))
    (kafka/create-publisher brokers)))

(defn init-http! [repository publisher]
  (let [port (get-in config [:http :port])]
    (http/start-server publisher repository port)))

(defn -main [& _]
  (println "Starting Order Processing System...")
  (try
    (let [repository (init-db!)
          publisher (init-kafka!)
          server (init-http! repository publisher)]
      (reset! system-state {:repository repository :publisher publisher :server server})
      (println "System started successfully!")
      (println "API available at http://localhost:8081")
      (println "Press Ctrl+C to stop")
      @(promise))
    (catch Exception e
      (println "Failed to start system:" (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))