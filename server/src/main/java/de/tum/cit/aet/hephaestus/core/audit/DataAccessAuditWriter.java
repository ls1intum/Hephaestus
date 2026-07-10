package de.tum.cit.aet.hephaestus.core.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import org.hibernate.Session;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes disclosure-audit rows. Failures propagate so named report data is not served unaudited. */
@Service
public class DataAccessAuditWriter {

    private final DataAccessEventRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DataAccessAuditWriter(DataAccessEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void recordReportView(Long workspaceId, Long actorUserId, Long subjectUserId) {
        record(workspaceId, actorUserId, subjectUserId, DataAccessResourceType.PRACTICE_REPORT);
    }

    @Transactional
    public void recordRosterView(Long workspaceId, Long actorUserId) {
        record(workspaceId, actorUserId, null, DataAccessResourceType.PRACTICE_ROSTER);
    }

    private void record(
        Long workspaceId,
        Long actorUserId,
        @Nullable Long subjectUserId,
        DataAccessResourceType resourceType
    ) {
        repository.save(DataAccessEvent.of(workspaceId, actorUserId, subjectUserId, resourceType, clock.instant()));
    }

    @Transactional
    public void purgeWorkspace(Long workspaceId) {
        entityManager
            .unwrap(Session.class)
            .doWork(connection -> {
                try (Statement marker = connection.createStatement()) {
                    marker.execute("SET LOCAL hephaestus.audit_purge = 'on'");
                }
                try (
                    PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM data_access_event WHERE workspace_id = ?"
                    )
                ) {
                    delete.setLong(1, workspaceId);
                    delete.executeUpdate();
                }
            });
    }
}
