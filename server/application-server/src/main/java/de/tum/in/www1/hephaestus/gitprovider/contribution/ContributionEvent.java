package de.tum.in.www1.hephaestus.gitprovider.contribution;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "contribution_event",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_contribution_event_source", columnNames = { "source_type", "source_id" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ContributionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ContributionSourceType sourceType;

    @NotNull
    @Column(nullable = false)
    private Long sourceId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @NotNull
    @Column(nullable = false)
    private Instant occurredAt;

    @NotNull
    @Column(nullable = false)
    private Integer xpAwarded;
}
