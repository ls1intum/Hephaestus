package de.tum.in.www1.hephaestus.gitprovider.contributions;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "contribution_event")
@Getter
@Setter
@NoArgsConstructor
public class ContributionEvent {
    @Id
    protected Long id;

    @NotNull
    @Column(name = "source_type", length = 128, nullable = false)
    private String sourceType;

    @NotNull
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @NotNull
    @Column(name = "occured_at", nullable = false)
    private Instant occuredAt;

    @NotNull
    @Column(name = "xp_awarded", nullable = false)
    private Integer xpAwarded;

}
