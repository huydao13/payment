CREATE TABLE saga_states (
     saga_id VARCHAR(255) PRIMARY KEY,
     user_id VARCHAR(255),
     product_id VARCHAR(255),
     quantity INTEGER,
     amount BIGINT,
     current_step VARCHAR(50),
     status VARCHAR(50),
     order_id VARCHAR(255),
     payment_id VARCHAR(255),
     retry_count INTEGER,
     error_message VARCHAR(1000),
     simulate_fail_at VARCHAR(50),
     created_at TIMESTAMP,
     updated_at TIMESTAMP
);
