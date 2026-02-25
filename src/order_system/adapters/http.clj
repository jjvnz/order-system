(ns order-system.adapters.http
  "Adapter HTTP con servidor HTTP simple"
  (:require [order-system.domain.ports :as ports]
            [order-system.domain.order :as domain]
            [order-system.domain.specs :as specs]
            [order-system.adapters.kafka :as kafka]
            [clojure.data.json :as json])
  (:import (com.sun.net.httpserver HttpServer HttpExchange HttpHandler)
           (java.net InetSocketAddress)
           (java.io InputStream ByteArrayOutputStream)))

(defn parse-body [^InputStream body-stream]
  (when body-stream
    (try
      (let [body (slurp body-stream)]
        (when (and (string? body) (not (empty? body)))
          (try
            (json/read-str body :key-fn keyword)
            (catch Exception _
              body))))
      (catch Exception _
        nil))))

(defn write-response [^HttpExchange exchange status body]
  (let [response-bytes (.getBytes ^String body "UTF-8")
        headers (.getResponseHeaders exchange)]
    (.put headers "Content-Type" ["application/json"])
    (.put headers "X-Content-Type-Options" ["nosniff"])
    (.sendResponseHeaders exchange status (long (count response-bytes)))
    (with-open [os (.getResponseBody exchange)]
      (.write os response-bytes))))

(defn write-json-response [^HttpExchange exchange status data]
  (write-response exchange status (json/write-str data)))

(defmulti handle-request (fn [method path] [method path]))

(defmethod handle-request :default [method path]
  (fn [^HttpExchange exchange]
    (write-json-response exchange 404 {:error "Not found" :method method :path path})))

(defn handle-create-order [repository publisher ^HttpExchange exchange]
  (let [body (parse-body (.getRequestBody exchange))]
    (if (nil? body)
      (write-json-response exchange 400 {:error "Empty request body"})
      (let [[valid? validation-data] (specs/validate-order body)]
        (if-not valid?
          (write-json-response exchange 400 {:errors validation-data})
          (try
            (let [customer-id (:customer-id body)
                  items (:items body)
                  order (domain/create-order customer-id items)
                  event (domain/create-order-event order :order-created)]
              (kafka/publish-event publisher kafka/order-events-topic event)
              (write-json-response exchange 202 {:order-id (str (:order-id order))
                                                 :status "accepted"
                                                 :message "Order queued for processing"}))
            (catch Exception e
              (println "[HTTP] Error creating order:" (.getMessage e))
              (write-json-response exchange 500 {:error "Internal server error" :message (.getMessage e)}))))))))

(defn handle-get-order [repository ^HttpExchange exchange order-id]
  (try
    (let [uuid (java.util.UUID/fromString order-id)
          order (ports/find-order repository uuid)]
      (if order
        (write-json-response exchange 200 (update order :order-id str))
        (write-json-response exchange 404 {:error "Order not found" :order-id order-id})))
    (catch Exception e
      (write-json-response exchange 400 {:error "Invalid order ID format"}))))

(defn handle-list-orders [repository ^HttpExchange exchange]
  (try
    (let [orders (ports/list-orders repository)]
      (write-json-response exchange 200 {:orders (mapv #(update % :order-id str) orders)}))
    (catch Exception e
      (println "[HTTP] Error listing orders:" (.getMessage e))
      (write-json-response exchange 500 {:error "Internal server error"}))))

(defn handle-health [^HttpExchange exchange]
  (write-json-response exchange 200 {:status "healthy" :service "order-system"}))

(defn route-request [repository publisher ^HttpExchange exchange]
  (let [uri (.getRequestURI exchange)
        method (.getRequestMethod exchange)
        path (.getPath uri)]
    (cond
      (and (= method "GET") (= path "/health"))
      (handle-health exchange)

      (and (= method "POST") (= path "/api/orders"))
      (handle-create-order repository publisher exchange)

      (and (= method "GET") (= path "/api/orders"))
      (handle-list-orders repository exchange)

      (and (= method "GET") (re-find #"^/api/orders/[^/]+$" path))
      (let [order-id (last (re-find #"/api/orders/([^/]+)" path))]
        (handle-get-order repository exchange order-id))

      :else
      (write-json-response exchange 404 {:error "Not found" :method method :path path}))))

(defrecord HttpServerInstance [server exchange-handler]
  Object
  (toString [_] (str "HttpServer on port " (.getPort (.getAddress (.getServer server)))))

  clojure.lang.IDeref
  (deref [_] (.isStopped server))

  clojure.lang.IFn
  (invoke [_ repository publisher]
    (route-request repository publisher exchange-handler)))

(defn start-server [publisher repository port]
  (println "[HTTP] Starting server on port" port)
  (let [server (HttpServer/create (InetSocketAddress. port) 0)
        context (.createContext server "/"
                                (reify HttpHandler
                                  (handle [this exchange]
                                    (try
                                      (route-request repository publisher exchange)
                                      (catch Exception e
                                        (println "[HTTP] Handler error:" (.getMessage e))
                                        (write-json-response exchange 500 {:error "Internal error"}))
                                      (finally
                                        (.close exchange))))))]
    (.setExecutor server (java.util.concurrent.Executors/newCachedThreadPool))
    (.start server)
    (println "[HTTP] Server started on port" port)
    {:server server
     :stop (fn []
             (println "[HTTP] Stopping server...")
             (.stop server 0)
             (println "[HTTP] Server stopped"))}))

(defn stop-server [server-instance]
  (when-let [stop-fn (:stop server-instance)]
    (stop-fn)))
