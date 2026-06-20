package com.saga.payment.repository;
import com.saga.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findBySagaId(String sagaId);
}
