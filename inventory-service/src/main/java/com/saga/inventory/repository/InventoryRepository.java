package com.saga.inventory.repository;
import com.saga.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryRepository extends JpaRepository<Inventory, String> {}
