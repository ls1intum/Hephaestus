package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Query-param filters shared by the instance-admin and workspace-scoped audit viewers, bound as a
 * flattened parameter object so each handler stays under the parameter-count budget.
 */
public record ConfigAuditFilterParams(
    @RequestParam(required = false) @Nullable ConfigAuditEntityType entityType,
    @RequestParam(required = false) @Nullable String entityId,
    @RequestParam(required = false) @Nullable String changedKey,
    @RequestParam(required = false) @Nullable ConfigAuditAction action,
    @RequestParam(required = false) @Nullable Long actorId,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant from,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant to
) {
    /** Hard cap so a typo'd or malicious {@code size} can't pull the whole trail in one request. */
    public static final int MAX_PAGE_SIZE = 200;

    public ConfigAuditFilter toFilter() {
        if (entityId != null && entityType == null) {
            // Entity id spaces are per-type, so an unqualified id would match across types by accident.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityId requires entityType");
        }
        return new ConfigAuditFilter(entityType, entityId, changedKey, action, actorId, from, to);
    }

    /** Clamps rather than rejects an oversized page, matching {@code AuthAuditController}. */
    public static Pageable pageable(int page, int size) {
        // The queries carry their own ORDER BY occurred_at DESC, id DESC; keep the Pageable sort empty.
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
