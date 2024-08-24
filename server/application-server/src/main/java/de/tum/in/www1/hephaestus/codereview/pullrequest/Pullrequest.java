package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;

import de.tum.in.www1.hephaestus.codereview.actor.Actor;
import de.tum.in.www1.hephaestus.codereview.comment.Comment;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pullrequests")
@Getter
@Setter
public class Pullrequest {

    /**
     * Unique identifier for a Pullrequest entity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id")
    private Long githubId;

    /**
     * Title of the Pullrequest.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String title;

    /**
     * URL of the Pullrequest.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String url;

    /**
     * State of the Pullrequest.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String state;

    /**
     * Timestamp of when the Pullrequest entity was created.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String createdAt;

    /**
     * Timestamp of when the Pullrequest entity was updated.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private String updatedAt;

    /**
     * Timestamp of when the Pullrequest entity was merged.
     */
    @Column(nullable = true)
    private String mergedAt;

    /**
     * The author of the Pullrequest entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Actor author;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pullrequest")
    private List<Comment> comments;

    /**
     * The parent connection of the Pullrequest entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", referencedColumnName = "id")
    private Repository repository;
}
