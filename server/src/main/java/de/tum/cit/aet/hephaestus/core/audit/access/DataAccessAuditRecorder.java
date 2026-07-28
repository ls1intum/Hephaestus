package de.tum.cit.aet.hephaestus.core.audit.access;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code data_access_event}.
 *
 * <p>One row per served response, with no dedup window: every response that discloses a named report is a
 * disclosure, so repeat rows from a re-opened dialog are true events. Failures propagate, so a read that
 * cannot be recorded is refused rather than served unrecorded.
 */
@Service
@RequiredArgsConstructor
public class DataAccessAuditRecorder implements DataAccessAuditPort {

    private final DataAccessEventRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public void recordDisclosure(
        Long workspaceId,
        Long actorUserId,
        @Nullable Long subjectUserId,
        DataAccessResourceType resourceType
    ) {
        repository.save(DataAccessEvent.of(workspaceId, actorUserId, subjectUserId, resourceType, clock.instant()));
    }

    /**
     * Erase a purged workspace's disclosure rows — the one deletion the trigger cannot authorise from the row
     * itself, because purge contributors run before the workspace flips to {@code PURGED}. The
     * transaction-local marker is scoped to this transaction and is the single place the append-only rule is
     * deliberately stood down.
     */
    @Override
    @Transactional
    public int purgeWorkspace(Long workspaceId) {
        jdbcTemplate.execute("SET LOCAL hephaestus.audit_purge = 'on'");
        return jdbcTemplate.update("DELETE FROM data_access_event WHERE workspace_id = ?", workspaceId);
    }
}
