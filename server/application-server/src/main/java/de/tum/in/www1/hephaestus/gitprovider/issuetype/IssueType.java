package de.tum.in.www1.hephaestus.gitprovider.issuetype;

import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Represents a GitHub Issue Type - an organization-level categorization for
 * issues.
 * <p>
 * Issue types are defined at the organization level and can be assigned to
 * issues
 * to categorize them (e.g., Bug, Feature, Task).
 * <p>
 * This maps to GitHub's IssueType GraphQL type.
 */
@Entity
@Table(name = "issue_type")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class IssueType {

    /**
     * GitHub's node_id (GraphQL ID) is used as the primary key.
     */
    @Id
    @Column(length = 128)
    private String id;

    @NonNull
    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Color color;

    @Column(nullable = false)
    private boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    private Organization organization;

    /**
     * Timestamp of the last successful sync for this issue type from the Git provider.
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this issue type
     * was last synchronized via GraphQL. Used to implement sync cooldown logic
     * and detect stale data.
     */
    private Instant lastSyncAt;

    /**
     * Issue type colors as defined in GitHub's IssueTypeColor enum.
     */
    public enum Color {
        BLUE,
        GRAY,
        GREEN,
        ORANGE,
        PINK,
        PURPLE,
        RED,
        YELLOW,
    }
}
