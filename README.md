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
│   ├── postgres.clj  # PostgreSQL persistence (next.jdbc)
│   ├── kafka.clj     # Apache Kafka client (producers/consumers)
│   └── http.clj      # REST API (Java HttpServer)
└── system.clj        # System composition
```

## Technology Stack

- **Language**: Clojure 1.11
- **Database**: PostgreSQL 15 (next.jdbc)
- **Messaging**: Apache Kafka 7.5 (kafka-clients)
- **Web API**: Java HttpServer (built-in)
- **Docker**: Docker Compose

## Prerequisites

- Docker & Docker Compose

## Quick Start

```bash
cd order-system
docker-compose up --build
```

The API will be available at `http://localhost:8081`

## API Endpoints

### Health Check
```bash
curl http://localhost:8081/health
```

Response:
```json
{
  "status": "healthy",
  "service": "order-system"
}
```

### Create Order (POST /api/orders)
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customer-id": "customer-123",
    "items": [
      {"product-id": "PROD-001", "quantity": 2, "unit-price": 29.99}
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

### Get Order (GET /api/orders/{order-id})
```bash
curl http://localhost:8081/api/orders/0225da6f-4ade-4930-9e2d-2b5795e904f3
```

### List Orders (GET /api/orders)
```bash
curl http://localhost:8081/api/orders
```

Response:
```json
{
  "orders": [
    {
      "order_id": "uuid",
      "customer_id": "customer-123",
      "items": [...],
      "total": 59.98,
      "status": "confirmed",
      "created_at": "2026-02-25T16:53:14.114580Z",
      "updated_at": "2026-02-25T16:53:14.138252Z"
    }
  ]
}
```

## Data Flow

```
1. POST /api/orders    → Validate with clojure.spec
2. Kafka Producer     → Publish order-created event to Kafka
3. HTTP 202           → Return immediately to client
4. Kafka Consumer     → Poll events from Kafka topic
5. PostgreSQL         → Persist order with status=confirmed
6. GET /api/orders    → Read from PostgreSQL
```

## Configuration

Environment variables (set in docker-compose.yml):
- `DATABASE_URL` - PostgreSQL connection string
- `KAFKA_BROKERS` - Kafka broker address (internal: kafka:29092, external: localhost:9092)
- `HTTP_PORT` - HTTP server port (default: 8081)
- `KAFKA_CONSUMER_GROUP` - Kafka consumer group name

## Docker Services

| Service       | Image                    | Port  | Description              |
|---------------|--------------------------|-------|-------------------------|
| postgres      | postgres:15-alpine       | 5432  | PostgreSQL database     |
| zookeeper     | confluentinc/cp-zookeeper:7.5.0 | 2181 | ZooKeeper        |
| kafka         | confluentinc/cp-kafka:7.5.0    | 9092  | Kafka broker     |
| order-system  | Clojure app              | 8081  | REST API          |

## Order Status Lifecycle

```
pending → confirmed → processing → completed
              ↓
           cancelled
```

## Testing with Postman

### Create Order
```json
POST http://localhost:8081/api/orders
Content-Type: application/json

{
  "customer-id": "juan-perez",
  "items": [
    {
      "product-id": "PROD-001",
      "quantity": 2,
      "unit-price": 29.99
    },
    {
      "product-id": "PROD-002",
      "quantity": 1,
      "unit-price": 49.99,
      "discount": 5.00
    }
  ]
}
```

## Clean Architecture Principles

- **Domain Layer**: Pure business logic, no external dependencies
- **Ports**: Clojure protocols define abstractions
- **Adapters**: Concrete implementations (PostgreSQL, Kafka)
- **Dependency Injection**: Manual DI in system.clj

## License

MIT
