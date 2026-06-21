CREATE TABLE payments (
  payment_id VARCHAR(255) PRIMARY KEY,
  saga_id VARCHAR(255),
  user_id VARCHAR(255),
  amount BIGINT,
  status VARCHAR(50),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE idempotency_keys (
  idempotency_key VARCHAR(255) PRIMARY KEY,
  status VARCHAR(50),
  payment_id VARCHAR(255),
  error_message VARCHAR(1000),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
