package de.tum.in.www1.hephaestus.codereview.comment;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import de.tum.in.www1.hephaestus.codereview.actor.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
public class Comment {
    /**
     * Unique identifier for a Comment entity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id")
    private String githubId;

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
    @OneToOne(optional = false)
    @JoinColumn(name = "c_connection_id", referencedColumnName = "id")
    private CommentConnection connection;

}
