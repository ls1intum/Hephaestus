package de.tum.cit.aet.hephaestus.practices.research;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The protected link from one key printed in a released dataset to the account it stands for, and to
 * the exact grant that allowed the release. It exists so that a withdrawal or an erasure can name
 * the key the researchers must remove. It never leaves the instance-admin research boundary except
 * as that key.
 */
@Entity
@Table(
        name = "research_disclosure_subject",
        check = {
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_key",
                    constraint = "participant_key ~ '^p_[a-z2-7]{26}$'"),
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_reason",
                    constraint =
                            "(removal_required_at IS NULL) = (removal_reason IS NULL) AND (removal_reason IS NULL OR removal_reason IN ('CONSENT_WITHDRAWN','CONSENT_SUPERSEDED','QUESTION_CHANGED','ACCOUNT_ERASED'))"),
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_removed",
                    constraint = "removed_at IS NULL OR removal_required_at IS NOT NULL"),
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_link",
                    constraint = "account_id IS NOT NULL OR removal_reason IS NOT NULL"),
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_pruned",
                    constraint = "removed_at IS NULL OR (account_id IS NULL AND grant_decision_id IS NULL)"),
            @CheckConstraint(
                    name = "ck_research_disclosure_subject_grant",
                    constraint = "grant_decision_id IS NOT NULL OR removed_at IS NOT NULL"),
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_research_disclosure_subject_key",
                    columnNames = {"disclosure_id", "participant_key"}),
            @UniqueConstraint(
                    name = "uk_research_disclosure_subject_account",
                    columnNames = {"disclosure_id", "account_id"})
        },
        indexes = {
            @Index(name = "idx_research_disclosure_subject_account", columnList = "account_id"),
            @Index(name = "idx_research_disclosure_subject_disclosure", columnList = "workspace_id, disclosure_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchDisclosureSubject {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "disclosure_id", nullable = false, updatable = false)
    private UUID disclosureId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private long workspaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(name = "disclosure_id", referencedColumnName = "id", insertable = false, updatable = false),
                @JoinColumn(
                        name = "workspace_id",
                        referencedColumnName = "workspace_id",
                        insertable = false,
                        updatable = false),
            },
            foreignKey = @ForeignKey(name = "fk_research_disclosure_subject_disclosure"))
    @Getter(AccessLevel.NONE)
    @SuppressWarnings("UnusedVariable") // Mapping-only: Hibernate declares the composite tenant foreign key.
    private @Nullable ResearchDisclosure tenantOwnedDisclosure;

    @Column(name = "account_id")
    private @Nullable Long accountId;

    @Column(name = "participant_key", nullable = false, length = 32, updatable = false)
    private String participantKey;

    @Column(name = "grant_decision_id")
    private @Nullable Long grantDecisionId;

    @Column(name = "removal_required_at")
    private @Nullable Instant removalRequiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 24)
    private @Nullable RemovalReason removalReason;

    @Column(name = "removed_at")
    private @Nullable Instant removedAt;

    public enum RemovalReason {
        CONSENT_WITHDRAWN,
        CONSENT_SUPERSEDED,
        QUESTION_CHANGED,
        ACCOUNT_ERASED,
    }

    ResearchDisclosureSubject(
            UUID disclosureId, long workspaceId, long accountId, String participantKey, long grantDecisionId) {
        this.id = UUID.randomUUID();
        this.disclosureId = disclosureId;
        this.workspaceId = workspaceId;
        this.accountId = accountId;
        this.participantKey = participantKey;
        this.grantDecisionId = grantDecisionId;
    }

    public boolean isRemovalOutstanding() {
        return removalReason != null && removedAt == null;
    }

    /** The first reason found stays: a later regrant or erasure does not rewrite why removal is owed. */
    void requireRemoval(RemovalReason reason, Instant at) {
        if (removalReason == null) {
            removalReason = reason;
            removalRequiredAt = at;
        }
    }

    /**
     * Records the operator's attestation and drops the identifying link. The key stays as a receipt
     * until the disclosure is closed.
     */
    void recordRemoved(Instant at) {
        if (removalReason == null) {
            throw new IllegalStateException("No removal is owed for this participant");
        }
        if (removedAt != null) {
            return;
        }
        if (at.isBefore(Objects.requireNonNull(removalRequiredAt))) {
            throw new IllegalArgumentException("Removal precedes the obligation");
        }
        removedAt = at;
        accountId = null;
        grantDecisionId = null;
    }
}
