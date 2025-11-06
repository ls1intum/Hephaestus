package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.organization.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.github.GHRepositorySelection;

@Entity
@Table(name = "workspace")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant usersSyncedAt;

    @OneToMany(
        mappedBy = "workspace",
        fetch = FetchType.EAGER,
        cascade = { CascadeType.PERSIST, CascadeType.REMOVE },
        orphanRemoval = true
    )
    @ToString.Exclude
    private Set<RepositoryToMonitor> repositoriesToMonitor = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private GitProviderMode gitProviderMode = GitProviderMode.PAT_ORG;

    @Column(name = "installation_id", unique = true)
    private Long installationId;

    private String accountLogin;

    @Column(name = "personal_access_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String personalAccessToken;

    @Enumerated(EnumType.STRING)
    private GHRepositorySelection githubRepositorySelection; // ALL / SELECTED

    private Instant installationLinkedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", unique = true, foreignKey = @ForeignKey(name = "fk_workspace_organization"))
    @ToString.Exclude
    private Organization organization;

    //TODO: Only temporary to differentiate between ls1intum <-> orgs installed via GHApp. To be deleted in the future
    public enum GitProviderMode {
        PAT_ORG,
        GITHUB_APP_INSTALLATION,
    }
}
