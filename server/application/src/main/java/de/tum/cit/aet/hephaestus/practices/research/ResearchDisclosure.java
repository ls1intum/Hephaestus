package de.tum.cit.aet.hephaestus.practices.research;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * One released research dataset. It holds what was released, under which question, and until when
 * it may be used. It never holds the payload. The composite parent key keeps each protected subject
 * mapping in the workspace that released it.
 */
@Entity
@Table(
        name = "research_disclosure",
        check = {
            @CheckConstraint(
                    name = "ck_research_disclosure_purpose",
                    constraint = "purpose = 'RESEARCH_PARTICIPATION'"),
            @CheckConstraint(
                    name = "ck_research_disclosure_schema",
                    constraint = "schema_name = 'hephaestus.research-dataset' AND schema_version > 0"),
            @CheckConstraint(
                    name = "ck_research_disclosure_window",
                    constraint = "window_from < window_to AND window_to <= cutoff_at + interval '1 day'"),
            @CheckConstraint(name = "ck_research_disclosure_hash", constraint = "content_sha256 ~ '^[0-9a-f]{64}$'"),
            @CheckConstraint(
                    name = "ck_research_disclosure_counts",
                    constraint =
                            "participant_count >= 0 AND result_count >= 0 AND problem_count >= 0 AND feedback_count >= 0 AND feedback_excluded_count >= 0"),
            @CheckConstraint(name = "ck_research_disclosure_use", constraint = "use_until > created_at"),
            @CheckConstraint(
                    name = "ck_research_disclosure_closure",
                    constraint =
                            "(closed_at IS NULL) = (closure IS NULL) AND (closure IS NULL OR closure IN ('DESTROYED','ANONYMIZED'))"),
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_research_disclosure_workspace",
                    columnNames = {"id", "workspace_id"})
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResearchDisclosure {

    public static final String PURPOSE = "RESEARCH_PARTICIPATION";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private long workspaceId;

    @Column(name = "purpose", nullable = false, length = 40, updatable = false)
    private String purpose;

    @Column(name = "schema_name", nullable = false, length = 64, updatable = false)
    private String schemaName;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Column(name = "window_from", nullable = false, updatable = false)
    private Instant windowFrom;

    @Column(name = "window_to", nullable = false, updatable = false)
    private Instant windowTo;

    @Column(name = "cutoff_at", nullable = false, updatable = false)
    private Instant cutoffAt;

    @Column(name = "notice_version", nullable = false, length = 32, updatable = false)
    private String noticeVersion;

    @Column(name = "research_organization", nullable = false, length = 255, updatable = false)
    private String researchOrganization;

    @Column(name = "created_by_account_id")
    private @Nullable Long createdByAccountId;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(name = "participant_count", nullable = false, updatable = false)
    private int participantCount;

    @Column(name = "result_count", nullable = false, updatable = false)
    private int resultCount;

    @Column(name = "problem_count", nullable = false, updatable = false)
    private int problemCount;

    @Column(name = "feedback_count", nullable = false, updatable = false)
    private int feedbackCount;

    @Column(name = "feedback_excluded_count", nullable = false, updatable = false)
    private int feedbackExcludedCount;

    @Column(name = "use_until", nullable = false, updatable = false)
    private Instant useUntil;

    @Column(name = "closed_at")
    private @Nullable Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure", length = 16)
    private @Nullable Closure closure;

    public enum Closure {
        /** Every retained copy was deleted. */
        DESTROYED,
        /** Every retained copy was anonymised, so no participant can be found in it any more. */
        ANONYMIZED,
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public boolean isUseExpired(Instant now) {
        return !now.isBefore(useUntil);
    }

    /** An attestation by the operator; the server cannot see the researchers' storage. */
    void close(Closure kind, Instant at) {
        if (closedAt != null) {
            throw new IllegalStateException("Disclosure already closed");
        }
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Closure precedes release");
        }
        closure = kind;
        closedAt = at;
    }
}
