package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
@DisplayName("GitLabGroupProcessor")
class GitLabGroupProcessorTest extends BaseUnitTest {

    private static final Long PROVIDER_ID = 1L;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    private GitLabGroupProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GitLabGroupProcessor(organizationRepository, gitProviderRepository);

        // Default: save returns its argument (lenient — not used by null-guard tests)
        lenient()
            .when(organizationRepository.save(any(Organization.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // Default: provider reference (lenient — not used by null-guard tests)
        lenient().when(gitProviderRepository.getReferenceById(PROVIDER_ID)).thenReturn(new GitProvider());
    }

    @Nested
    @DisplayName("process")
    class Process {

        @Test
        @DisplayName("valid group creates new organization with correct field mapping")
        void validGroup_createsOrganization() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org/my-team",
                "My Team",
                "https://gitlab.com/avatar.png",
                "https://gitlab.com/my-org/my-team",
                "Team description",
                "public",
                null
            );

            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(Optional.empty());

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isEqualTo(42L);
            assertThat(result.getLogin()).isEqualTo("my-org/my-team");
            assertThat(result.getName()).isEqualTo("My Team");
            assertThat(result.getAvatarUrl()).isEqualTo("https://gitlab.com/avatar.png");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/my-org/my-team");
            assertThat(result.getLastSyncAt()).isNotNull();
            assertThat(result.getCreatedAt()).isNotNull();
            verify(organizationRepository).save(any(Organization.class));
        }

        @Test
        @DisplayName("existing org updates mutable fields and preserves createdAt")
        void existingOrg_updatesFieldsPreservesCreatedAt() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org/renamed",
                "Renamed Org",
                "https://gitlab.com/new-avatar.png",
                "https://gitlab.com/my-org/renamed",
                null,
                "public",
                null
            );

            Instant existingCreatedAt = Instant.parse("2024-01-01T00:00:00Z");
            Organization existing = new Organization();
            existing.setId(100L);
            existing.setNativeId(42L);
            existing.setCreatedAt(existingCreatedAt);
            existing.setLogin("my-org/old-name");
            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getCreatedAt()).isEqualTo(existingCreatedAt);
            assertThat(result.getLogin()).isEqualTo("my-org/renamed");
            assertThat(result.getName()).isEqualTo("Renamed Org");
            assertThat(result.getAvatarUrl()).isEqualTo("https://gitlab.com/new-avatar.png");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/my-org/renamed");
            assertThat(result.getLastSyncAt()).isNotNull();
        }

        @Test
        @DisplayName("null name falls back to fullPath as name")
        void nullName_fallsBackToFullPath() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/99",
                "org/team",
                null,
                null,
                "https://gitlab.com/org/team",
                null,
                "private",
                null
            );

            when(organizationRepository.findByNativeIdAndProviderId(99L, PROVIDER_ID)).thenReturn(Optional.empty());

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("org/team");
        }

        @Test
        @DisplayName("deeply nested group path is preserved")
        void deeplyNestedPath_preserved() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/7",
                "org/team/subteam/deepteam",
                "Deep Team",
                null,
                "https://gitlab.com/org/team/subteam/deepteam",
                null,
                "internal",
                null
            );

            when(organizationRepository.findByNativeIdAndProviderId(7L, PROVIDER_ID)).thenReturn(Optional.empty());

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getLogin()).isEqualTo("org/team/subteam/deepteam");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/org/team/subteam/deepteam");
        }

        @Test
        @DisplayName("new org sets createdAt, existing org does not overwrite it")
        void timestampSemantics() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Org",
                null,
                "https://gitlab.com/my-org",
                null,
                "public",
                null
            );

            // First call: new entity
            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(Optional.empty());
            Organization firstResult = processor.process(group, PROVIDER_ID);

            assertThat(firstResult).isNotNull();
            Instant firstCreatedAt = firstResult.getCreatedAt();
            assertThat(firstCreatedAt).isNotNull();

            // Second call: existing entity with createdAt already set
            Organization existing = new Organization();
            existing.setId(100L);
            existing.setNativeId(42L);
            existing.setCreatedAt(firstCreatedAt);
            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization secondResult = processor.process(group, PROVIDER_ID);

            assertThat(secondResult).isNotNull();
            assertThat(secondResult.getCreatedAt()).isEqualTo(firstCreatedAt);
        }

        @Test
        @DisplayName("null response returns null")
        void nullResponse_returnsNull() {
            assertThat(processor.process(null, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("null id returns null")
        void nullId_returnsNull() {
            var group = new GitLabGroupResponse(
                null,
                "org/team",
                "Team",
                null,
                "https://gitlab.com/org/team",
                null,
                "public",
                null
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("null fullPath returns null")
        void nullFullPath_returnsNull() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                null,
                "Team",
                null,
                "https://gitlab.com",
                null,
                "public",
                null
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("null webUrl returns null")
        void nullWebUrl_returnsNull() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "org/team",
                "Team",
                null,
                null, // null webUrl
                null,
                "public",
                null
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("invalid GID format returns null")
        void invalidGid_returnsNull() {
            var group = new GitLabGroupResponse(
                "invalid-id-format",
                "org/team",
                "Team",
                null,
                "https://gitlab.com/org/team",
                null,
                "public",
                null
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).save(any());
        }
    }
}
