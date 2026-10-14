package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * The original public writer inputs of one admitted review: the finite authorized records and whole standards it
 * was written from, kept so a writer lost after admission can be replaced without assessing again. Cold by design:
 * no association reaches it from {@code ReviewJob}, and only a composition reads its payload, by job.
 *
 * <p>The row is scoped to its job's workspace and removed with the job. Its admission attempt and digest are the
 * identity the admitted citations were verified under; {@code copyId} is the person-evidence receipt that indexes
 * whose rows the payload copied, so erasing any of them removes the row before another writer reads it.
 */
@Entity
@Table(name = "review_composition_basis")
@Getter
@Setter
public class ReviewCompositionBasis {

    public static final int VERSION = 1;

    /**
     * Uses the debug transcript's one-day eligibility window. An expired basis is refused; the existing retention
     * sweep removes it.
     */
    public static final Duration RETENTION = JobEvidenceFiles.TRACE_RETENTION;

    /**
     * The {@link ProvenanceDigest#rootDigestHex(Map) root digest} of the retained files: over their paths and decoded
     * bytes, so neither how jsonb orders the stored object nor how it re-encodes it changes the digest.
     */
    public static String digest(Map<String, byte[]> files) {
        return ProvenanceDigest.rootDigestHex(files);
    }

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "admission_attempt", nullable = false)
    private int admissionAttempt;

    @Column(name = "admission_digest", nullable = false, length = 64)
    private String admissionDigest;

    @Column(name = "basis_version", nullable = false)
    private int basisVersion;

    /** {@link #digest(Map)} of the payload's decoded files, checked before a writer receives them. */
    @Column(name = "basis_digest", nullable = false, length = 64)
    private String basisDigest;

    @Column(name = "copy_id", nullable = false)
    private UUID copyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;
}
