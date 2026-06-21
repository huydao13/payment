CREATE TABLE orders (
    order_id VARCHAR(255) PRIMARY KEY,
    saga_id VARCHAR(255),
    user_id VARCHAR(255),
    product_id VARCHAR(255),
    quantity INTEGER,
    amount BIGINT,
    status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
