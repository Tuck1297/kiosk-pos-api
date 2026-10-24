package io.github.tuck1297.kioskpos.location;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<LocationEntity, UUID> {

    /** First page, ordered by id so cursors are stable. */
    List<LocationEntity> findAllByOrderByIdAsc(Limit limit);

    /** Next page: everything after the last id the client saw. */
    List<LocationEntity> findByIdGreaterThanOrderByIdAsc(UUID afterId, Limit limit);
}
