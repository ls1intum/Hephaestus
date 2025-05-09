package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BadPracticeDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    @ToString.Exclude
    private PullRequest pullRequest;

    @NonNull
    private String summary;

    @NonNull
    @OneToMany
    @ToString.Exclude
    private List<PullRequestBadPractice> badPractices;

    @NonNull
    private OffsetDateTime detectionTime;

    private String traceId;
}
