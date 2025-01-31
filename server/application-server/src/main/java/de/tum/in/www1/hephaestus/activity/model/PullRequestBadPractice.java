package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name="pullrequestbadpractice")
public class PullRequestBadPractice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String title;

    @NonNull
    private String description;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    private PullRequest pullrequest;

    private boolean resolved;
}
