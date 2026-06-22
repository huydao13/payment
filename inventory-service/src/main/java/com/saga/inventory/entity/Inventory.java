package com.saga.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optimistic Locking với @Version
 *
 * Tại sao cần?
 * → 500 người đặt vé cùng lúc, còn 1 vé
 * → Không có @Version → 500 người đều thấy còn vé → 500 người được đặt!
 * → Có @Version → chỉ 1 người thắng, 499 người nhận OptimisticLockException
 */
@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
public class Inventory {

    @Id
    private String productId;

    private String productName;
    private Integer quantity;
    private Long price;

    private String imageUrl;

    /**
     * @Version tự động tăng mỗi lần UPDATE
     * Nếu 2 request cùng đọc version=5, cùng muốn UPDATE:
     * → Request A: UPDATE WHERE version=5 → thành công, version=6
     * → Request B: UPDATE WHERE version=5 → FAIL! version hiện tại là 6 rồi
     * → OptimisticLockException → retry hoặc báo lỗi
     */
    @Version
    private Integer version;

    public Inventory(String productId, String productName, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Inventory(String productId, String productName, Integer quantity,
        Long price, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.imageUrl = imageUrl;
    }
}
