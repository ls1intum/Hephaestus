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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContributionSourceType sourceType;

    @NotNull
    private Long sourceId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @NotNull
    private Instant occurredAt;

    @NotNull
    private Integer xpAwarded;

}
