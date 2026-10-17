package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewCadence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
        name = "review_schedule",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_review_schedule_workspace_id",
                    columnNames = {"id", "workspace_id"}),
            @UniqueConstraint(
                    name = "uk_review_schedule_workspace_kind",
                    columnNames = {"workspace_id", "kind"})
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSchedule {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Default
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private long version = 0L;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "kind", nullable = false, length = 64)
    private String kind;

    @Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DAILY'")
    @Column(name = "cadence", nullable = false, length = 16)
    private ReviewCadence cadence = ReviewCadence.DAILY;

    @Column(name = "lookback_days")
    private @Nullable Integer lookbackDays;

    @Column(name = "budget_usd", precision = 18, scale = 6)
    private @Nullable BigDecimal budgetUsd;

    @Default
    @ColumnDefault("true")
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private @Nullable Instant lastRunAt;

    @Column(name = "created_by_account_id")
    private @Nullable Long createdByAccountId;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
