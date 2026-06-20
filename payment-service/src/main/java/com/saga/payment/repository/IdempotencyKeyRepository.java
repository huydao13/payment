package com.saga.payment.repository;
import com.saga.payment.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {}
