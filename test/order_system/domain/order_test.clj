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

(deftest calculate-total-empty-items-test
  (testing "returns 0 for empty items"
    (is (= 0 (domain/calculate-total [])))))

(deftest calculate-line-total-test
  (testing "calculates line total without discount"
    (is (= 100.0 (domain/calculate-line-total {:quantity 2 :unit-price 50.0}))))
  (testing "calculates line total with discount"
    (is (= 80.0 (domain/calculate-line-total {:quantity 2 :unit-price 50.0 :discount 20.0}))))
  (testing "returns 0 if discount exceeds line total"
    (is (= 0 (domain/calculate-line-total {:quantity 1 :unit-price 10.0 :discount 15.0})))))

(deftest create-order-test
  (testing "creates valid order"
    (let [order (domain/create-order "customer-1"
                                      [{:product-id "P1" :quantity 2 :unit-price 10.0}])]
      (is (= "customer-1" (:customer-id order)))
      (is (= 20.0 (:total order)))
      (is (= :pending (:status order)))
      (is (uuid? (:order-id order))))))

  (testing "throws on empty customer-id"
    (is (thrown? clojure.lang.ExceptionInfo (domain/create-order "" [{:product-id "P1" :quantity 1 :unit-price 10.0}]))))

  (testing "throws on empty items"
    (is (thrown? clojure.lang.ExceptionInfo (domain/create-order "c1" [])))))

(deftest validate-order-specs-test
  (testing "validates valid order request"
    (let [[valid? errors] (specs/validate-order {:customer-id "c1"
                                                  :items [{:product-id "p1"
                                                           :quantity 1
                                                           :unit-price 10.0}]})]
      (is (true? valid?))))

  (testing "rejects order without customer-id"
    (let [[valid? errors] (specs/validate-order {:items [{:product-id "p1" :quantity 1 :unit-price 10.0}]})]
      (is (false? valid?))))

  (testing "rejects order with empty items"
    (let [[valid? errors] (specs/validate-order {:customer-id "c1" :items []})]
      (is (false? valid?)))))

(deftest confirm-order-test
  (testing "changes status to confirmed"
    (let [order {:order-id "123" :status :pending}]
      (is = :confirmed (:status (domain/confirm-order order))))))

(deftest cancel-order-test
  (testing "changes status to cancelled"
    (let [order {:order-id "123" :status :pending}]
      (is = :cancelled (:status (domain/cancel-order order))))))

(deftest process-order-event-test
  (testing "processes :order-created event"
    (let [order {:order-id "123" :status :pending}]
      (is (= :confirmed (:status (domain/process-order-event order :order-created))))))

  (testing "processes :order-cancelled event"
    (let [order {:order-id "123" :status :pending}]
      (is (= :cancelled (:status (domain/process-order-event order :order-cancelled))))))

  (testing "returns unchanged for unknown event"
    (let [order {:order-id "123" :status :pending}]
      (is (= order (domain/process-order-event order :unknown-event))))))
