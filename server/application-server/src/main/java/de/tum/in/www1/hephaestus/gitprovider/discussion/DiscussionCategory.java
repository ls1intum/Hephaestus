package de.tum.in.www1.hephaestus.gitprovider.discussion;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Entity representing a GitHub Discussion Category.
 * <p>
 * Discussion categories are repository-scoped and determine whether
 * discussions can have accepted answers (Q&amp;A format).
 * <p>
 * Note: Unlike most entities, this uses a String ID (node_id) because
 * GitHub's GraphQL API doesn't expose databaseId for DiscussionCategory.
 */
@Entity
@Table(name = "discussion_category", uniqueConstraints = @UniqueConstraint(columnNames = { "repository_id", "slug" }))
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DiscussionCategory {

    /**
     * The GitHub node ID (e.g., "DIC_kwDOBk...").
     * Used as primary key since databaseId is not available for categories.
     */
    @Id
    @Column(length = 128)
    @EqualsAndHashCode.Include
    private String id;

    @NonNull
    private String name;

    @NonNull
    @Column(length = 128)
    private String slug;

    @Column(length = 32)
    private String emoji;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Whether discussions in this category support choosing an answer
     * with the markDiscussionCommentAsAnswer mutation (Q&amp;A format).
     */
    private boolean isAnswerable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    @OneToMany(mappedBy = "category")
    @ToString.Exclude
    private List<Discussion> discussions = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;
}
