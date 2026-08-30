package de.tum.cit.aet.hephaestus.productfeedback;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
        name = "product_feedback",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_product_feedback_rate_limit",
                        columnNames = {"account_id", "submission_minute"}))
@Getter
@NoArgsConstructor
public class ProductFeedback {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "workspace_id")
    private @Nullable Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 5000)
    private String message;

    @Column(name = "page_path", length = 500)
    private @Nullable String pagePath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(name = "submission_minute", nullable = false, updatable = false)
    private Instant submissionMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);

    public ProductFeedback(
            Long accountId, @Nullable Long workspaceId, Kind kind, String message, @Nullable String pagePath) {
        this.accountId = accountId;
        this.workspaceId = workspaceId;
        this.kind = kind;
        this.message = message;
        this.pagePath = pagePath;
    }

    public enum Kind {
        FEEDBACK,
        BUG
    }
}
