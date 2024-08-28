package de.tum.in.www1.hephaestus.codereview.comment;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.actor.Actor;
import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Comment {
    /**
     * Unique identifier for a Comment entity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id")
    private Long githubId;

    /**
     * Body of the Comment entity.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String body;

    /**
     * Timestamp of when the Comment entity was created.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String createdAt;

    /**
     * Timestamp of when the Comment entity was updated.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String updatedAt;

    /**
     * The author of the Comment entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Actor author;

    /**
     * The parent connection to the pullrequest of the Comment entity.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "pullrequest_id", referencedColumnName = "id")
    @JsonIgnore
    private Pullrequest pullrequest;

    public Comment(String body, String createdAt, String updatedAt) {
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Comment [id=" + id + ", githubId=" + githubId + ", body=" + body + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + ", author=" + author.getLogin() + ", pullrequest="
                + pullrequest.getTitle() + "]";
    }
}