# Order Processing System

Event-Driven Order Processing System built with **Clean Architecture** principles using Clojure.

## Architecture

```
src/order_system/
├── domain/           # Pure business logic (no infrastructure dependencies)
│   ├── ports.clj     # Protocols (interfaces)
│   ├── specs.clj     # clojure.spec validations
│   └── order.clj     # Domain entities and logic
├── adapters/         # Infrastructure implementations
│   ├── sqlite.clj    # SQLite persistence (OrderRepository)
│   ├── kafka.clj     # Kafka messaging (MessagePublisher)
│   └── http.clj      # REST API (reitit + aleph)
└── system.clj        # System composition with Integrant
```

## Technology Stack

- **Language**: Clojure 1.11.1
- **Lifecycle**: Integrant
- **Database**: SQLite (next.jdbc)
- **Messaging**: Kafka (jackdaw)
- **Web API**: reitit + muuntaja + aleph

## Prerequisites

- Docker & Docker Compose

## Quick Start

```bash
# Start the system (Zookeeper + Kafka + App)
cd order-system
docker-compose up
```

The API will be available at `http://localhost:8080`

## API Endpoints

### Create Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customer-id": "customer-123",
    "items": [
      {"product-id": "P1", "quantity": 2, "unit-price": 10.0}
    ]
  }'
```

Response (202 Accepted):
```json
{
  "order-id": "uuid",
  "status": "accepted",
  "message": "Order queued for processing"
}
```

### Get Order
```bash
curl http://localhost:8080/api/orders/{order-id}
```

### List Orders
```bash
curl http://localhost:8080/api/orders
```

## Flow

1. **HTTP Request** → Validates payload with `clojure.spec`
2. **Publish to Kafka** → Returns 202 (Accepted)
3. **Kafka Consumer** → Persists to SQLite asynchronously

## Testing

```bash
# Run unit tests
clojure -M:test -m clojure.main -e "(require '[order-system.domain.order-test] :reload)"
```

Example test with mock injection:
```clojure
(defrecord MockRepository []
  ports/OrderRepository
  (save-order [this order] (assoc order :saved true)))

(deftest test-with-mock
  (let [repo (MockRepository.)]
    (is (:saved (ports/save-order repo {})))))
```

## Configuration

Edit `resources/config.edn`:
```edn
{:database-url "jdbc:sqlite:orders.db"
 :kafka {:brokers ["localhost:9092"]}
 :http {:port 8080}}
```

## Docker Services

- **zookeeper**: ZooKeeper (port 2181)
- **kafka**: Kafka broker (port 9092)
- **app**: Clojure application (port 8080)

## Clean Architecture Principles

- **Domain Layer**: Pure business logic, no external dependencies
- **Ports**: Clojure protocols define abstractions
- **Adapters**: Concrete implementations (SQLite, Kafka)
- **Dependency Injection**: Integrant manages lifecycle

## License

MIT
