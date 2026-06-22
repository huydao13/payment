ALTER TABLE inventory ADD COLUMN IF NOT EXISTS price BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);

UPDATE inventory SET price = 250000,
                     image_url = 'https://images.unsplash.com/photo-1599669454515-1b2e0173f302?w=400&h=300&fit=crop'
WHERE product_id = 'product-A';

UPDATE inventory SET price = 180000,
                     image_url = 'PLACEHOLDER_IMAGE_URL_B'
WHERE product_id = 'product-B';

INSERT INTO inventory (product_id, product_name, quantity, price, image_url, version)
VALUES ('product-C', 'Tai nghe không dây', 20, 590000, 'PLACEHOLDER_IMAGE_URL_C', 0)
    ON CONFLICT (product_id) DO NOTHING;

INSERT INTO inventory (product_id, product_name, quantity, price, image_url, version)
VALUES ('product-D', 'Balo laptop', 15, 420000, 'PLACEHOLDER_IMAGE_URL_D', 0)
    ON CONFLICT (product_id) DO NOTHING;

INSERT INTO inventory (product_id, product_name, quantity, price, image_url, version)
VALUES ('product-E', 'Bàn phím cơ', 8, 990000, 'PLACEHOLDER_IMAGE_URL_E', 0)
    ON CONFLICT (product_id) DO NOTHING;

INSERT INTO inventory (product_id, product_name, quantity, price, image_url, version)
VALUES ('product-F', 'Chuột không dây', 30, 350000, 'PLACEHOLDER_IMAGE_URL_F', 0)
    ON CONFLICT (product_id) DO NOTHING;
