package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "pullrequestbadpractice")
public class PullRequestBadPractice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String title;

    @Lob
    @NonNull
    private String description;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    private PullRequest pullrequest;

    @NonNull
    private PullRequestBadPracticeState state;

    private PullRequestBadPracticeState userState;

    private OffsetDateTime detectionTime;

    private OffsetDateTime lastUpdateTime;

    private PullRequestLifecycleState detectionPullrequestLifecycleState;

    private UUID detectionTraceId;
}
