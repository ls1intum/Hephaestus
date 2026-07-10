package de.tum.cit.aet.hephaestus.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/** Append-only disclosure record written when a privileged view exposes practice-report data. */
@Entity
@Immutable
@Table(
    name = "data_access_event",
    indexes = {
        @Index(name = "idx_data_access_event_subject", columnList = "workspace_id, subject_user_id, resource_type"),
    }
)
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

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "subject_user_id")
    @Nullable
    private Long subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 48)
    private DataAccessResourceType resourceType;

    static DataAccessEvent of(
        Long workspaceId,
        Long actorUserId,
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
