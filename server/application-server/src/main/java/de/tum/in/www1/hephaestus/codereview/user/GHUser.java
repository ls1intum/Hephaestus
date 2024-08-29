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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login identifier for a user.
     */
    @NonNull
    private String login;

    @Column
    private String email;

    /**
     * Display name of the user.
     */
    @Column
    private String name;

    /**
     * Unique URL to the user's profile.
     * Not the website a user can set in their profile.
     */
    @NonNull
    private String url;

    /**
     * URL to the user's avatar.
     * If unavailable, a fallback can be generated from the login, e.g. on Github:
     * https://github.com/{login}.png
     */
    @NonNull
    private String avatarUrl;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    @JsonIgnore
    private Set<PullRequest> pullRequests = new HashSet<>();;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    @JsonIgnore
    private Set<IssueComment> comments = new HashSet<>();;

    public void addComment(IssueComment comment) {
        comments.add(comment);
    }

    public void addPullrequest(PullRequest pullrequest) {
        pullRequests.add(pullrequest);
    }
}
