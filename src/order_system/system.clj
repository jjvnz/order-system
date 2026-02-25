(ns order-system.system
  "Composición del sistema"
  (:gen-class)
  (:require [order-system.adapters.postgres :as postgres]
            [order-system.adapters.kafka :as kafka]
            [order-system.adapters.http :as http]
            [next.jdbc :as jdbc]
            [aero.core :as aero]
            [clojure.java.io :as io])
  (:import (java.time Instant)))

(def config
  (let [env #(or (System/getenv %) %)
        db-url (env "DATABASE_URL")
        brokers (env "KAFKA_BROKERS")
        port (Long/parseLong (env "HTTP_PORT"))
        consumer-group (or (System/getenv "KAFKA_CONSUMER_GROUP") "order-processor")]
    {:database-url db-url
     :kafka {:brokers (if (string? brokers)
                        (clojure.string/split brokers #",")
                        ["kafka:29092"])
             :consumer-group consumer-group}
     :http {:port port}}))

(def system-state (atom nil))

(defn parse-database-url [db-url]
  (if-let [matches (re-find #"jdbc:postgresql://([^:]+):(\d+)/(\w+)\?user=(\w+)&password=(\w+)" db-url)]
    {:host (second matches)
     :port (Integer/parseInt (nth matches 2))
     :dbname (nth matches 3)
     :user (nth matches 4)
     :password (nth matches 5)}
    {:host "localhost"
     :port 5432
     :dbname "orders_db"
     :user "orders"
     :password "orders_password"}))

(defn init-db! []
  (let [db-url (:database-url config)
        db-config (assoc (parse-database-url db-url) :dbtype "postgresql")]
    (println "Connecting to PostgreSQL" (:host db-config) "...")
    (let [ds (jdbc/get-datasource db-config)]
      (postgres/init-schema! ds)
      (postgres/create-repository ds))))

(defn init-kafka! []
  (let [brokers (get-in config [:kafka :brokers])
        consumer-group (get-in config [:kafka :consumer-group])]
    (try
      (kafka/ensure-topic! brokers kafka/order-events-topic)
      (catch Exception e
        (println "Topic creation warning:" (.getMessage e))))
    {:publisher (kafka/create-publisher brokers)
     :consumer-group consumer-group}))

(defn init-http! [repository publisher]
  (let [port (get-in config [:http :port])]
    (http/start-server publisher repository port)))

(defn -main [& _]
  (println "Starting Order Processing System...")
  (println "Config:" (pr-str config))
  (try
    (let [repository (init-db!)
          {:keys [publisher consumer-group]} (init-kafka!)
          _ (kafka/start-order-consumer! repository publisher consumer-group (get-in config [:kafka :brokers]))
          server (init-http! repository publisher)]
      (reset! system-state {:repository repository :publisher publisher :server server})
      (println "System started successfully!")
      (println "API available at http://localhost:" (get-in config [:http :port]))
      (println "Press Ctrl+C to stop")
      @(promise))
    (catch Exception e
      (println "Failed to start system:" (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))
