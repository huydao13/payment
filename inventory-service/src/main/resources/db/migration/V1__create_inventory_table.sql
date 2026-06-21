CREATE TABLE inventory (
   product_id VARCHAR(255) PRIMARY KEY,
   product_name VARCHAR(255),
   quantity INTEGER,
   version INTEGER
);

CREATE TABLE inventory_reservations (
    saga_id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255),
    quantity INTEGER,
    status VARCHAR(50),
    created_at TIMESTAMP
);
