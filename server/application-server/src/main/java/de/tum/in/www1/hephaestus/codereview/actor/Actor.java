package de.tum.in.www1.hephaestus.codereview.actor;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.comment.Comment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "actor")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Actor {
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
    @Column(nullable = false)
    private String login;

    @Column
    private String email;

    /**
     * URL of the User entity.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String url;

    /**
     * The Pullrequests of the User entity.
     */
    @OneToMany(mappedBy = "author")
    @JsonIgnore
    private Set<Pullrequest> pullrequests;

    /**
     * The Comments of the User entity.
     */
    @OneToMany(mappedBy = "author")
    @JsonIgnore
    private Set<Comment> comments;

    public void addComment(Comment comment) {
        if (comments == null) {
            comments = new HashSet<>();
        }
        if (!comments.contains(comment)) {
            comments.add(comment);
        }
    }

    public void addPullrequest(Pullrequest pullrequest) {
        if (pullrequests == null) {
            pullrequests = new HashSet<>();
        }
        if (!pullrequests.contains(pullrequest)) {
            pullrequests.add(pullrequest);
        }
    }

    @Override
    public String toString() {
        return "Actor [id=" + id + ", login=" + login + ", email=" + email + ", url="
                + url + ", #pullrequests="
                + pullrequests.size() + ", #comments=" + comments.size() + "]";
    }
}
