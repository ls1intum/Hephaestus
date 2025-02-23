package de.tum.in.www1.hephaestus.analytics;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "analysis_results")
@Data
@NoArgsConstructor
public class AnalysisResults {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "analysis_type", nullable = false)
    private String analysisType;

    @Column(name = "time_period_start", nullable = false)
    private Instant timePeriodStart;

    @Column(name = "time_period_end", nullable = false)
    private Instant timePeriodEnd;

    @Type(JsonBinaryType.class)
    @Column(name = "analysis_data", columnDefinition = "jsonb")
    private Map<String, Object> analysisData;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}