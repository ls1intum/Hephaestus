package de.tum.in.www1.hephaestus.codereview.user;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "gh_user")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GHUser {
    /**
     * Unique identifier for a User entity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Login of the User entity.
     * This field is mandatory.
     */
    @NonNull
    private String login;

    @Column
    private String email;

    @Column
    private String name;

    /**
     * URL of the User entity.
     * This field is mandatory.
     */
    @NonNull
    private String url;

    /**
     * The Pullrequests of the User entity.
     */
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    @JsonIgnore
    private Set<PullRequest> pullRequests = new HashSet<>();;

    /**
     * The Comments of the User entity.
     */
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    @JsonIgnore
    private Set<IssueComment> comments = new HashSet<>();;

    public void addComment(IssueComment comment) {
        if (!comments.contains(comment)) {
            comments.add(comment);
        }
    }

    public void addPullrequest(PullRequest pullrequest) {
        if (!pullRequests.contains(pullrequest)) {
            pullRequests.add(pullrequest);
        }
    }
}
