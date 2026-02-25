(ns order-system.adapters.kafka
  "Adapter para Kafka - implementa MessagePublisher y EventConsumer"
  (:require [order-system.domain.ports :as ports]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (java.util Properties UUID)
           (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
           (org.apache.kafka.clients.consumer KafkaConsumer ConsumerRecord)
           (org.apache.kafka.common.errors WakeupException)))

(def ^:const order-events-topic "order-events")

(def ^:const default-retry-max 3)
(def ^:const default-retry-delay-ms 1000)

(defn create-producer [bootstrap-servers]
  (doto (Properties.)
    (.put "bootstrap.servers" bootstrap-servers)
    (.put "key.serializer" "org.apache.kafka.common.serialization.StringSerializer")
    (.put "value.serializer" "org.apache.kafka.common.serialization.StringSerializer")
    (.put "acks" "all")
    (.put "retries" (int default-retry-max))
    (.put "retry.backoff.ms" (int default-retry-delay-ms))
    (.put "delivery.timeout.ms" "120000")
    (.put "request.timeout.ms" "30000")
    (.put "linger.ms" "10")))

(defn create-publisher [brokers]
  (let [server-list (str/join "," brokers)
        producer (KafkaProducer. (create-producer server-list))]
    {:producer producer
     :brokers server-list
     :topic order-events-topic}))

(defn publish-event [publisher topic event]
  (let [{:keys [producer]} publisher]
    (try
      (let [record (ProducerRecord. topic
                                     (str (:order-id (:payload event)))
                                     (json/write-str event))
            future (.send producer record)
            _ (.get future 30000 java.util.concurrent.TimeUnit/MILLISECONDS)]
        event)
      (catch Exception e
        (println "[Kafka Publisher] Error publishing event:" (.getMessage e))
        (throw e)))))

(defn flush-publisher [publisher]
  (try
    (.flush ^KafkaProducer (:producer publisher))
    (catch Exception e
      (println "[Kafka Publisher] Flush error:" (.getMessage e)))))

(defn close-publisher [publisher]
  (try
    (flush-publisher publisher)
    (.close ^KafkaProducer (:producer publisher) 5000 java.util.concurrent.TimeUnit/MILLISECONDS)
    (catch Exception e
      (println "[Kafka Publisher] Close error:" (.getMessage e)))))

(defrecord KafkaPublisher [producer config]
  ports/MessagePublisher

  (publish-event [_ topic event]
    (publish-event producer topic event))

  (subscribe [_ topic handler]
    (println "[Kafka Publisher] Subscribe not implemented - use consumer instead")))

(defn create-consumer [brokers consumer-group repository]
  (let [server-list (str/join "," brokers)
        config (doto (Properties.)
                 (.put "bootstrap.servers" server-list)
                 (.put "group.id" consumer-group)
                 (.put "auto.offset.reset" "earliest")
                 (.put "enable.auto.commit" "false")
                 (.put "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
                 (.put "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer")
                 (.put "max.poll.interval.ms" "300000")
                 (.put "heartbeat.interval.ms" "3000")
                 (.put "session.timeout.ms" "10000"))
        consumer (KafkaConsumer. config)]
    (.subscribe consumer [order-events-topic])
    consumer))

(defn process-event [repository event]
  (let [{:keys [event-type payload]} event
        event-type (keyword event-type)]
    (cond
      (= event-type :order-created)
      (do
        (println "[Kafka Consumer] Processing order-created:" (:order-id payload))
        (ports/save-order repository (assoc payload :status :confirmed)))

      (= event-type :order-cancelled)
      (do
        (println "[Kafka Consumer] Processing order-cancelled:" (:order-id payload))
        (ports/save-order repository (assoc payload :status :cancelled)))

      :else
      (println "[Kafka Consumer] Unknown event type:" event-type))))

(defrecord OrderEventConsumer [consumer repository consumer-group]
  ports/EventConsumer

  (start-consuming [_]
    (let [running (atom true)]
      (future
        (while @running
          (try
            (let [records (.poll consumer 5000)
                  _ (when (pos? (.count records))
                      (println "[Kafka Consumer] Processing" (.count records) "records"))]
              (doseq [^ConsumerRecord record records]
                (try
                  (let [event (json/read-str (.value record) :key-fn keyword)]
                    (process-event repository event)
                    (.commitSync consumer))
                  (catch Exception e
                    (println "[Kafka Consumer] Error processing record:" (.getMessage e))))))
            (catch WakeupException _
              (println "[Kafka Consumer] Wakeup received"))
            (catch Exception e
              (println "[Kafka Consumer] Consumer error:" (.getMessage e))
              (Thread/sleep 5000)))))
      {:stop! (fn [] (reset! running false) (.wakeup consumer))})))

(defn ensure-topic! [brokers topic-name]
  (let [server-list (str/join "," brokers)
        admin-props (doto (Properties.)
                      (.put "bootstrap.servers" server-list))
        admin (org.apache.kafka.clients.admin.AdminClient/create ^Properties admin-props)]
    (try
      (let [topics (.listTopics admin)
            existing (.names topics)]
        (when-not (.contains existing topic-name)
          (let [new-topic (org.apache.kafka.clients.admin.NewTopic. topic-name (int 1) (short 1))
                configs (.allConfigs new-topic)]
            (.createTopics admin (java.util.Collections/singleton configs))))
        (println "Topic" topic-name "ensured"))
      (catch Exception e
        (println "Topic creation/verification warning:" (.getMessage e)))
      (finally
        (.close admin 5000 java.util.concurrent.TimeUnit/MILLISECONDS)))))

(defn start-order-consumer! [repository publisher consumer-group brokers]
  (let [consumer (create-consumer brokers consumer-group repository)]
    (ports/start-consuming (OrderEventConsumer. consumer repository consumer-group))))
