(ns order-system.adapters.kafka
  "Adapter para Kafka - implementa MessagePublisher y EventConsumer"
  (:require [jackdaw.client :as jc]
            [jackdaw.admin :as ja]
            [jackdaw.serializers :as js]
            [order-system.domain.ports :as ports]
            [order-system.domain.order :as domain])
  (:import (java.util Properties UUID)))

(defrecord KafkaPublisher [producer config]
  ports/MessagePublisher

  (publish-event [this topic event]
    (let [record {:topic-name topic
                  :key (str (:order-id (:payload event)))
                  :value event}]
      (jc/send! producer record)
      event)))

(defn create-publisher [brokers]
  (let [config {"bootstrap.servers" (first brokers)
                "key.serializer" "org.apache.kafka.common.serialization.StringSerializer"
                "value.serializer" "org.apache.kafka.common.serialization.StringSerializer"
                "acks" "all"}
        producer (jc/producer config)]
    (KafkaPublisher. producer {:brokers brokers})))

(defrecord KafkaConsumer [consumer handler]
  ports/EventConsumer

  (start-consuming [this]
    (future
      (while true
        (let [records (.poll consumer 1000)]
          (doseq [record records]
            (handler (.value record))))))))

(defrecord KafkaSubscriber [consumer config]
  ports/MessagePublisher

  (subscribe [this topic handler]
    (let [props (doto (Properties.)
                  (.put "bootstrap.servers" (first (:brokers config)))
                  (.put "group.id" (str (UUID/randomUUID)))
                  (.put "auto.offset.reset" "earliest")
                  (.put "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
                  (.put "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"))
          consumer (jc/consumer props)
          subscription (jc/subscribe consumer [topic])]
      (KafkaConsumer. consumer handler))))

(defn ensure-topic! [brokers topic-name]
  (let [admin-config {"bootstrap.servers" (first brokers)}]
    (with-open [admin-client (ja/admin-client admin-config)]
      (when-not (ja/topic-exists? admin-client topic-name)
        (ja/create-topic! admin-client
                         (ja/new-topic topic-name
                                      {:num-partitions 3
                                       :replication-factor 1}))))))

(def order-events-topic "order-events")

(defn start-order-consumer! [publisher repository brokers]
  (let [config {:brokers brokers}
        subscriber (map->KafkaSubscriber {:config config})]
    (ports/subscribe subscriber
                    order-events-topic
                    (fn [event-str]
                      (let [event (read-string event-str)
                            order (:payload event)]
                        (when (= :order-created (:event-type event))
                          (ports/save-order repository order)))))))
