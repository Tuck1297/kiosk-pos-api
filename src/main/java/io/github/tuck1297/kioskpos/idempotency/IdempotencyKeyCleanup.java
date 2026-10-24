package io.github.tuck1297.kioskpos.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired Idempotency-Keys (kept 24 hours) in small batches, so the table stays
 * small and the delete never holds long locks next to live checkout traffic.
 *
 * Every API replica runs this. SKIP LOCKED lets them share the work: rows one replica is
 * deleting are skipped by the others instead of waited on.
 */
@Component
public class IdempotencyKeyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);
    private static final int BATCH = 5_000;

    private final JdbcTemplate jdbc;

    public IdempotencyKeyCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelayString = "${kiosk.idempotency.cleanup-initial-delay:PT1M}",
               fixedDelayString = "${kiosk.idempotency.cleanup-interval:PT10M}")
    public void purgeExpired() {
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update("""
                    DELETE FROM idempotency_key
                    WHERE ctid IN (
                        SELECT ctid FROM idempotency_key
                        WHERE expires_at < now()
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED)""", BATCH);
            total += deleted;
        } while (deleted == BATCH);
        if (total > 0) {
            log.info("Purged {} expired idempotency keys", total);
        }
    }
}
