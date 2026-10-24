package io.github.tuck1297.kioskpos.idempotency;

import io.github.tuck1297.kioskpos.common.ApiException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key bookkeeping. Both methods MUST run inside the caller's transaction,
 * the same one that does the work:
 *
 * <ol>
 *   <li>{@link #claim} inserts the key. A concurrent request with the same key blocks on
 *       the primary key until this transaction ends.</li>
 *   <li>The caller does the work, then {@link #complete} records which row it produced.</li>
 *   <li>Commit makes the key and the work visible together; a rollback removes both, so a
 *       failed request can simply be retried.</li>
 * </ol>
 *
 * A retry with the same key and the same request gets the stored result back; the same
 * key with a different request is rejected.
 */
@Component
public class IdempotencyStore {

    public record StoredResult(int status, UUID resourceId) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the earlier result if this exact request was already completed, else empty (go ahead). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StoredResult> claim(String scope, UUID key, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_key (scope, idempotency_key, request_hash, state)
                VALUES (?, ?, ?, 'IN_PROGRESS')
                ON CONFLICT DO NOTHING""", scope, key, requestHash);
        if (inserted == 1) {
            return Optional.empty();
        }

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT request_hash, state, response_status, resource_id
                FROM idempotency_key
                WHERE scope = ? AND idempotency_key = ?""", scope, key);
        if (!requestHash.equals(String.valueOf(row.get("request_hash")).strip())) {
            throw ApiException.idempotencyKeyReused();
        }
        if (!"COMPLETED".equals(row.get("state"))) {
            throw ApiException.idempotencyInProgress();
        }
        return Optional.of(new StoredResult(
                ((Number) row.get("response_status")).intValue(),
                (UUID) row.get("resource_id")));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String scope, UUID key, int status, UUID resourceId, String resourcePath) {
        jdbc.update("""
                UPDATE idempotency_key
                SET state = 'COMPLETED', response_status = ?, resource_id = ?, resource_path = ?
                WHERE scope = ? AND idempotency_key = ?""", status, resourceId, resourcePath, scope, key);
    }
}
