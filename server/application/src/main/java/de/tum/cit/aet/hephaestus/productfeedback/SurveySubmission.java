package de.tum.cit.aet.hephaestus.productfeedback;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Entity
@Table(
        name = "product_survey_submission",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_survey_submission_account",
                        columnNames = {"survey_id", "account_id"}))
@Getter
@NoArgsConstructor
public class SurveySubmission {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "workspace_id")
    private @Nullable Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Disposition disposition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_json", columnDefinition = "jsonb")
    private @Nullable JsonNode answers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    public SurveySubmission(
            UUID surveyId,
            Long accountId,
            @Nullable Long workspaceId,
            Disposition disposition,
            @Nullable JsonNode answers) {
        this.surveyId = surveyId;
        this.accountId = accountId;
        this.workspaceId = workspaceId;
        this.disposition = disposition;
        this.answers = answers;
    }

    public enum Disposition {
        RESPONDED,
        DISMISSED
    }
}
