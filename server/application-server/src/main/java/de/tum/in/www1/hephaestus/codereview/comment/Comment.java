package de.tum.in.www1.hephaestus.codereview.comment;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private Long createdAt;

    /**
     * Timestamp of when the Comment entity was updated.
     * This field is mandatory.
     */
    @Column(nullable = false)
    private Long updatedAt;

    /**
     * The author of the Comment entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Actor author;

    /**
     * The pullrequest of the Comment entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pullrequest_id")
    private Pullrequest pullrequest;
}
