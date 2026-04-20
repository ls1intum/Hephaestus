package de.tum.in.www1.hephaestus.gitprovider.issuetype;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the provider-scoped {@code IssueType} name lookup used as
 * a fallback when a GitLab issue lives in a subgroup whose {@code Organization}
 * row has not had its own seed {@code issue_type} entries materialised yet.
 *
 * <p>Gap #3 reconciliation: {@code GitLabIssueTypeSyncService} only seeds types
 * under the workspace's root {@code accountLogin} organization, but each GitLab
 * subgroup gets its own {@code Organization} row. Because {@code issue_type}
 * primary keys are GitLab-global GraphQL IDs, resolving by provider-scoped name
 * yields the same row regardless of which organization owns it.
 */
@DisplayName("IssueTypeRepository provider-scoped name fallback Integration")
class IssueTypeRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IssueTypeRepository issueTypeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    private GitProvider gitlabProvider;
    private GitProvider otherProvider;
    private Organization rootOrg;
    private Organization subgroupOrg;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        gitlabProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.example.com")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.example.com"))
            );

        otherProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://other.gitlab.com")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://other.gitlab.com"))
            );

        rootOrg = persistOrg(gitlabProvider, 1000L, "root-group");
        subgroupOrg = persistOrg(gitlabProvider, 1001L, "root-group/subgroup");
    }

    private Organization persistOrg(GitProvider provider, long nativeId, String login) {
        Organization org = new Organization();
        org.setNativeId(nativeId);
        org.setLogin(login);
        org.setHtmlUrl("https://gitlab.example.com/" + login);
        org.setProvider(provider);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        return organizationRepository.save(org);
    }

    private IssueType persistIssueType(String id, String name, Organization org) {
        IssueType type = new IssueType();
        type.setId(id);
        type.setName(name);
        type.setColor(IssueType.Color.BLUE);
        type.setEnabled(true);
        type.setOrganization(org);
        return issueTypeRepository.save(type);
    }

    @Test
    @DisplayName("falls back to provider-scoped match when the subgroup has no seed rows")
    void resolvesTypeFromRootWhenSubgroupHasNoSeedRows() {
        persistIssueType("gid://gitlab/WorkItems::Type/1", "Issue", rootOrg);

        Optional<IssueType> resolved = issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(
            gitlabProvider.getId(),
            "Issue"
        );

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo("gid://gitlab/WorkItems::Type/1");
        assertThat(resolved.get().getOrganization().getId()).isEqualTo(rootOrg.getId());
    }

    @Test
    @DisplayName("match is case-insensitive")
    void matchIsCaseInsensitive() {
        persistIssueType("gid://gitlab/WorkItems::Type/2", "Task", rootOrg);

        assertThat(
            issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(gitlabProvider.getId(), "task")
        ).isPresent();
        assertThat(
            issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(gitlabProvider.getId(), "TASK")
        ).isPresent();
        assertThat(
            issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(gitlabProvider.getId(), "tAsK")
        ).isPresent();
    }

    @Test
    @DisplayName("does not cross provider boundaries")
    void doesNotCrossProviderBoundaries() {
        Organization otherOrg = persistOrg(otherProvider, 2000L, "other-group");
        persistIssueType("gid://other/WorkItems::Type/99", "Bug", otherOrg);

        Optional<IssueType> resolved = issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(
            gitlabProvider.getId(),
            "Bug"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("skips disabled types")
    void skipsDisabledTypes() {
        IssueType disabled = persistIssueType("gid://gitlab/WorkItems::Type/3", "Incident", rootOrg);
        disabled.setEnabled(false);
        issueTypeRepository.save(disabled);

        Optional<IssueType> resolved = issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(
            gitlabProvider.getId(),
            "Incident"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("prefers the root organization when multiple orgs share the same type name")
    void prefersRootOrganizationWhenDuplicateNames() {
        IssueType rootType = persistIssueType("gid://gitlab/WorkItems::Type/10", "Epic", rootOrg);
        persistIssueType("gid://gitlab/WorkItems::Type/11", "Epic", subgroupOrg);

        Optional<IssueType> resolved = issueTypeRepository.findFirstByProviderIdAndNameIgnoreCase(
            gitlabProvider.getId(),
            "Epic"
        );

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(rootType.getId());
    }
}
