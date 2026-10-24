package io.github.tuck1297.kioskpos.discount;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountCodeRepository extends JpaRepository<DiscountCodeEntity, UUID> {

    Optional<DiscountCodeEntity> findByCode(String code);
}
