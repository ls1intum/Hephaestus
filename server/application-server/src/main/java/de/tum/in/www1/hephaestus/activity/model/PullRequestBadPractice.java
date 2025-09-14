package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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

    @Column(columnDefinition = "text")
    @NonNull
    private String description;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    private PullRequest pullrequest;

    @NonNull
    private PullRequestBadPracticeState state;

    private PullRequestBadPracticeState userState;

    private Instant detectionTime;

    private Instant lastUpdateTime;

    private PullRequestLifecycleState detectionPullrequestLifecycleState;

    private String detectionTraceId;

    @ManyToOne
    @JoinColumn(name = "bad_practice_detection_id")
    private BadPracticeDetection badPracticeDetection;
}
