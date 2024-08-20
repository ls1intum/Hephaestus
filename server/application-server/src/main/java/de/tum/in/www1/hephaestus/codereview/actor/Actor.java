package de.tum.in.www1.hephaestus.codereview.actor;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;

import de.tum.in.www1.hephaestus.codereview.comment.Comment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
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
    private List<Pullrequest> pullrequests;

    /**
     * The Comments of the User entity.
     */
    @OneToMany(mappedBy = "author")
    private List<Comment> comments;
}
