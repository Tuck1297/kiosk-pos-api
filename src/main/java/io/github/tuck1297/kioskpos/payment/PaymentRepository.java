package io.github.tuck1297.kioskpos.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
