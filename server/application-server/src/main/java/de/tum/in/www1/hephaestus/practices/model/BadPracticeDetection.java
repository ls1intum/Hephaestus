package de.tum.in.www1.hephaestus.practices.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.*;
import java.time.Instant;
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
    @Column(columnDefinition = "TEXT")
    private String summary;

    @NonNull
    @OneToMany(mappedBy = "badPracticeDetection", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<PullRequestBadPractice> badPractices;

    @NonNull
    @Column(name = "detected_at")
    private Instant detectedAt;

    private String traceId;
}
