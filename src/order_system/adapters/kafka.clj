(ns order-system.adapters.kafka
  "Adapter para Kafka - implementa MessagePublisher y EventConsumer"
  (:require [order-system.domain.ports :as ports]
            [clojure.data.json :as json])
  (:import (java.util Properties UUID)
           (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
           (org.apache.kafka.clients.consumer ConsumerRecord)))

(defn create-producer [bootstrap-servers]
  (doto (Properties.)
    (.put "bootstrap.servers" bootstrap-servers)
    (.put "key.serializer" "org.apache.kafka.common.serialization.StringSerializer")
    (.put "value.serializer" "org.apache.kafka.common.serialization.StringSerializer")
    (.put "acks" "all")
    (.put "retries" "3")))

(defrecord KafkaPublisher [producer _config]
  ports/MessagePublisher

  (publish-event [_ topic event]
    (try
      (let [record (ProducerRecord. topic
                                     (str (:order-id (:payload event)))
                                     (json/write-str event))]
        (.send producer record)
        event)
      (catch Exception e
        (println "Error publishing to Kafka:" (.getMessage e))
        event))))

(defn create-publisher [brokers]
  (let [producer (KafkaProducer. (create-producer (first brokers)))]
    (KafkaPublisher. producer {:brokers brokers})))

(defrecord OrderEventConsumer [consumer repository]
  ports/EventConsumer

  (start-consuming [_]
    (future
      (loop []
        (try
          (let [records (.poll consumer 1000)]
            (doseq [^ConsumerRecord record records]
              (let [event (json/read-str (.value record) :key-fn keyword)
                    order (:payload event)]
                (when (= :order-created (:event-type event))
                  (ports/save-order repository order)))))
          (catch Exception e
            (println "Consumer error:" (.getMessage e))))
        (recur)))))

(def order-events-topic "order-events")

(defn ensure-topic! [_ topic-name]
  (println "Topic" topic-name "will be auto-created on first use"))

(defn create-consumer [brokers repository]
  (let [config (doto (Properties.)
                  (.put "bootstrap.servers" (first brokers))
                  (.put "group.id" (str (UUID/randomUUID)))
                  (.put "auto.offset.reset" "earliest")
                  (.put "enable.auto.commit" "true")
                  (.put "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
                  (.put "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"))
        consumer (org.apache.kafka.clients.consumer.KafkaConsumer. config)
        _ (.subscribe consumer [order-events-topic])]
    (OrderEventConsumer. consumer repository)))

(defn start-order-consumer! [_ repository brokers]
  (let [consumer (create-consumer brokers repository)]
    (ports/start-consuming consumer)))
