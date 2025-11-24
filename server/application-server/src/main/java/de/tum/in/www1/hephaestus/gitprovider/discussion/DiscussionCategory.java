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
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "discussion_category",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_discussion_category_repo_slug",
        columnNames = { "repository_id", "slug" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DiscussionCategory {

    @Id
    private Long id;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(length = 128, nullable = false)
    private String slug;

    @Column(length = 64)
    private String emoji;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean answerable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    @ToString.Exclude
    private Repository repository;

    @OneToMany(mappedBy = "category")
    @ToString.Exclude
    private Set<Discussion> discussions = new HashSet<>();
}
