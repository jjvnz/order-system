(ns order-system.system
  "Composición del sistema con Integrant"
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [order-system.adapters.sqlite :as sqlite]
            [order-system.adapters.kafka :as kafka]
            [order-system.adapters.http :as http]
            [order-system.domain.ports :as ports]
            [clojure.java-time :as jt]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.time Duration)))

(def config
  (edn/read-string (slurp (io/resource "config.edn"))))

(defmethod ig/init-key :database/datasource [_ config]
  (let [db-url (:database-url config)]
    (println "Connecting to database:" db-url)
    (jdbc/get-datasource {:dbtype "sqlite"
                          :dbname (last (clojure.string/split db-url #"/"))})))

(defmethod ig/init-key :database/repository [_ datasource]
  (sqlite/init-schema! datasource)
  (sqlite/create-repository datasource))

(defmethod ig/init-key :kafka/publisher [_ config]
  (let [brokers (get-in config [:kafka :brokers])]
    (kafka/ensure-topic! brokers kafka/order-events-topic)
    (kafka/create-publisher brokers)))

(defmethod ig/init-key :http/server [_ config]
  (let [{:keys [port http kafka database]} config
        datasource (:database/datasource config)
        repository (:database/repository config)
        publisher (:kafka/publisher config)]
    (http/start-server publisher repository port)))

(defmethod ig/halt-key! :http/server [_ server]
  (.close server))

(defmethod ig/init-key :kafka/consumer [_ config]
  (let [{:keys [kafka database]} config
        datasource (:database/datasource config)
        repository (:database/repository config)
        publisher (:kafka/publisher config)
        brokers (get-in kafka [:brokers])]
    (kafka/start-order-consumer! publisher repository brokers)))

(defn start-system []
  (ig/init config))

(defn stop-system [system]
  (ig/halt! system))

(comment
  (def system (start-system))
  (stop-system system))
