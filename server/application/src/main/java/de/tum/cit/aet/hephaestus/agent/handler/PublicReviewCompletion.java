package de.tum.cit.aet.hephaestus.agent.handler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The whole review on the work a running review submitted once its public writer finished, accepted before its private
 * composition ends. The first acceptance is final: a repeat with the same review is acknowledged, any other refused,
 * and the job's terminal output can neither replace it nor post it again. It was committed together with what the
 * review then put on its way to the work: the automatic package's dispatch intent, the approval item, or the quiet and
 * withheld ledger rows. Delivery itself stays with the existing dispatcher.
 *
 * <p>Cold by design: no association reaches it from {@code AgentJob}, nothing lists it, and the row goes with its job.
 * {@code admissionAttempt} and {@code admissionDigest} name the admission whose citations the review rests on;
 * {@code acceptedAttempt} and {@code acceptedWorkerId} the attempt that wrote it, which differs after a writer was
 * continued. The review prose is cleared once the job's payload retention passes; the digest stays.
 */
@Entity
@Table(name = "public_review_completion")
@Getter
@Setter
public class PublicReviewCompletion {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    /** {@code ComposedReview.CONTRACT_VERSION} of the accepted review. */
    @Column(name = "contract_version", nullable = false)
    private int contractVersion;

    @Column(name = "admission_attempt", nullable = false)
    private int admissionAttempt;

    @Column(name = "admission_digest", nullable = false, length = 64)
    private String admissionDigest;

    @Column(name = "accepted_attempt", nullable = false)
    private int acceptedAttempt;

    @Column(name = "accepted_worker_id", nullable = false)
    private String acceptedWorkerId;

    /** SHA-256 of the server-normalized review, so a repeat compares by value and not by its JSON spelling. */
    @Column(name = "review_digest", nullable = false, length = 64)
    private String reviewDigest;

    /** The server-normalized whole review: summary, line notes and withholding decisions. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private @Nullable JsonNode review;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
