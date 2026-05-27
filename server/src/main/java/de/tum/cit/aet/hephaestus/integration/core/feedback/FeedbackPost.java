package de.tum.cit.aet.hephaestus.integration.core.feedback;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Cross-vendor record of a single feedback artefact posted back to a vendor.
 *
 * <p>Powers edit-in-place: when re-running practice review against the same subject,
 * the agent layer looks up existing posts via {@link FeedbackPostService} and edits
 * them rather than posting duplicates. Works uniformly across GitHub PR comments,
 * GitLab MR notes, Slack {@code chat.update}, and Outline {@code comments.update}.
 */
@Entity
@Table(
    name = "feedback_post",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_feedback_post",
        columnNames = { "connection_id", "subject_external_id", "post_kind", "external_post_id" }
    )
)
public class FeedbackPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "subject_external_id", nullable = false, length = 256)
    private String subjectExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_class", nullable = false, length = 48)
    private SubjectClass subjectClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_kind", nullable = false, length = 48)
    private PostKind postKind;

    @Column(name = "external_post_id", nullable = false, length = 256)
    private String externalPostId;

    @Column(name = "vendor_metadata", columnDefinition = "jsonb", nullable = false)
    private String vendorMetadata = "{}";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeedbackPost() {}

    public FeedbackPost(
        Connection connection,
        String subjectExternalId,
        SubjectClass subjectClass,
        PostKind postKind,
        String externalPostId,
        String vendorMetadata
    ) {
        this.connection = connection;
        this.subjectExternalId = subjectExternalId;
        this.subjectClass = subjectClass;
        this.postKind = postKind;
        this.externalPostId = externalPostId;
        this.vendorMetadata = vendorMetadata == null ? "{}" : vendorMetadata;
    }

    public enum PostKind {
        SUMMARY,
        INLINE_FINDING,
        APPROVAL,
    }

    public Long getId() {
        return id;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getSubjectExternalId() {
        return subjectExternalId;
    }

    public SubjectClass getSubjectClass() {
        return subjectClass;
    }

    public PostKind getPostKind() {
        return postKind;
    }

    public String getExternalPostId() {
        return externalPostId;
    }

    public String getVendorMetadata() {
        return vendorMetadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
