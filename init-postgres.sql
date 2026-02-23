-- Create orders table in the orders_db database (created automatically by POSTGRES_DB)
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    items JSONB NOT NULL,
    total DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'confirmed', 'processing', 'completed', 'cancelled')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_created_at ON orders(created_at);
