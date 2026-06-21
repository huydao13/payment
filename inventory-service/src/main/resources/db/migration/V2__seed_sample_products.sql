INSERT INTO inventory (product_id, product_name, quantity, version)
VALUES ('product-A', 'Sản phẩm A', 10, 0)
    ON CONFLICT (product_id) DO NOTHING;

INSERT INTO inventory (product_id, product_name, quantity, version)
VALUES ('product-B', 'Sản phẩm B', 5, 0)
    ON CONFLICT (product_id) DO NOTHING;
