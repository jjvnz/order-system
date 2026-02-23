(ns order-system.domain.order-test
  (:require [clojure.test :refer :all]
            [order-system.domain.order :as domain]
            [order-system.domain.specs :as specs]
            [order-system.domain.ports :as ports]))

(deftest calculate-total-test
  (testing "calculates total correctly"
    (let [items [{:product-id "P1" :quantity 2 :unit-price 10.0}
                 {:product-id "P2" :quantity 1 :unit-price 20.0}]]
      (is (= 40.0 (domain/calculate-total items))))))

(deftest calculate-total-with-discount-test
  (testing "applies discount correctly"
    (let [items [{:product-id "P1" :quantity 1 :unit-price 100.0 :discount 10.0}]]
      (is (= 90.0 (domain/calculate-total items))))))

(deftest create-order-test
  (testing "creates valid order"
    (let [order (domain/create-order "customer-1"
                                      [{:product-id "P1" :quantity 2 :unit-price 10.0}])]
      (is (= "customer-1" (:customer-id order)))
      (is (= 20.0 (:total order)))
      (is (= :pending (:status order)))
      (is (uuid? (:order-id order))))))

(deftest validate-order-specs-test
  (testing "validates order request"
    (is (specs/validate-order {:customer-id "c1"
                                :items [{:product-id "p1"
                                         :quantity 1
                                         :unit-price 10.0}]}))
    (is (not (specs/validate-order {:customer-id "c1"})))))

(defrecord MockRepository []
  ports/OrderRepository
  (save-order [this order] (assoc order :saved true))
  (find-order [this order-id] {:order-id order-id :status :found})
  (list-orders [this] [{:order-id "1"}]))

(defrecord MockPublisher []
  ports/MessagePublisher
  (publish-event [this topic event] event)
  (subscribe [this topic handler] :subscribed))

(deftest order-repository-protocol-mock-test
  (testing "mocking OrderRepository protocol"
    (let [repo (MockRepository.)
          result (ports/save-order repo {:order-id "123"})]
      (is (:saved result))
      (is (= "123" (:order-id result))))))

(deftest message-publisher-protocol-mock-test
  (testing "mocking MessagePublisher protocol"
    (let [publisher (MockPublisher.)
          event {:order-id "123" :event-type :test}]
      (is (= event (ports/publish-event publisher "topic" event))))))

(deftest process-order-event-test
  (testing "processes :order-created event"
    (let [order {:order-id "123" :status :pending}]
      (is (= :confirmed (:status (domain/process-order-event order :order-created))))))

  (testing "processes :order-cancelled event"
    (let [order {:order-id "123" :status :pending}]
      (is (= :cancelled (:status (domain/process-order-event order :order-cancelled)))))))
