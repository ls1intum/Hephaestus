package de.tum.in.www1.hephaestus.analytics;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "insight_recommendations")
@Data
@NoArgsConstructor
public class InsightRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResults analysisResult;

    @Column(name = "recommendation_type", nullable = false)
    private String recommendationType;

    @Column(name = "recommendation_text", nullable = false)
    private String recommendationText;

    private Integer priorityLevel;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}