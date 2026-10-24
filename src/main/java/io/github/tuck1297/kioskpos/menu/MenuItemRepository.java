package io.github.tuck1297.kioskpos.menu;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {

    List<MenuItemEntity> findByLocationId(UUID locationId);
}
