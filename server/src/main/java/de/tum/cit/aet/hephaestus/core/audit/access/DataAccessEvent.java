package de.tum.cit.aet.hephaestus.core.audit.access;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One disclosure: who was shown whose data, on which surface, when.
 *
 * <p>Append-only, enforced twice over — {@link Immutable} stops Hibernate from ever issuing an UPDATE for a
 * managed instance, and a {@code prod}-context trigger stops the database from accepting one (see
 * {@code package-info} for the carve-outs).
 *
 * <p>The identity columns are SCM actor ({@code "user"}) ids and carry <b>no FK</b>, unlike
 * {@code config_audit_event}'s account references. Two reasons: {@code "user"} is a synced mirror whose rows
 * are erased wholesale by {@code ScmWorkspaceContentEraser}, and a FK there would either block that erase or
 * cascade the disclosure record away with it — and a disclosure that happened stays true after the mirror is
 * gone. Both are nullable so erasure can unlink them without deleting the row.
 */
@Entity
@Immutable
@Table(name = "data_access_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataAccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** SCM actor id of the viewer. Null only after erasure unlinked it. */
    @Column(name = "actor_user_id")
    @Nullable
    private Long actorUserId;

    /**
     * SCM actor id of the person whose data was shown. Null for a bulk view (the roster discloses many
     * subjects at once, so no single id describes it) and after erasure unlinked it.
     */
    @Column(name = "subject_user_id")
    @Nullable
    private Long subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 48)
    private DataAccessResourceType resourceType;

    static DataAccessEvent of(
        Long workspaceId,
        @Nullable Long actorUserId,
        @Nullable Long subjectUserId,
        DataAccessResourceType resourceType,
        Instant occurredAt
    ) {
        DataAccessEvent event = new DataAccessEvent();
        event.workspaceId = workspaceId;
        event.actorUserId = actorUserId;
        event.subjectUserId = subjectUserId;
        event.resourceType = resourceType;
        event.occurredAt = occurredAt;
        return event;
    }
}
