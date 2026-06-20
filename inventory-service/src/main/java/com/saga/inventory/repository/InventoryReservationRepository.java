package com.saga.inventory.repository;
import com.saga.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {}
