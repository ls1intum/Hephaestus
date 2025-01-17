package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PullRequestBadPractice {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rule_id")
    private PullRequestBadPracticeRule rule;

    @ManyToOne
    @JoinColumn(name = "pullrequest_id")
    private PullRequest pullrequest;

    private boolean resolved;
}
