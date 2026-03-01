package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("GitLabGroupProcessor")
class GitLabGroupProcessorTest extends BaseUnitTest {

    private static final Long PROVIDER_ID = 1L;

    @Mock
    private OrganizationRepository organizationRepository;

    private GitLabGroupProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GitLabGroupProcessor(organizationRepository);
    }

    @Nested
    @DisplayName("process")
    class Process {

        @Test
        @DisplayName("valid group creates organization with correct field mapping")
        void validGroup_createsOrganization() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org/my-team",
                "My Team",
                "https://gitlab.com/avatar.png",
                "https://gitlab.com/my-org/my-team",
                "Team description",
                "public"
            );

            Organization expected = new Organization();
            expected.setId(100L);
            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(
                Optional.of(expected)
            );

            Organization result = processor.process(group, PROVIDER_ID);

            verify(organizationRepository).upsert(
                eq(42L),
                eq(PROVIDER_ID),
                eq("my-org/my-team"),
                eq("My Team"),
                eq("https://gitlab.com/avatar.png"),
                eq("https://gitlab.com/my-org/my-team")
            );
            assertThat(result).isNotNull();
            assertThat(result.getLastSyncAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("existing org with createdAt preserves it on re-sync")
        void existingOrg_preservesCreatedAt() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Org",
                null,
                "https://gitlab.com/my-org",
                null,
                "public"
            );

            Instant existingCreatedAt = Instant.parse("2024-01-01T00:00:00Z");
            Organization existing = new Organization();
            existing.setId(100L);
            existing.setCreatedAt(existingCreatedAt);
            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getCreatedAt()).isEqualTo(existingCreatedAt);
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getLastSyncAt()).isNotNull();
        }

        @Test
        @DisplayName("findByNativeIdAndProviderId returns empty after upsert returns null")
        void findByIdEmpty_returnsNull() {
            var group = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Org",
                null,
                "https://gitlab.com/my-org",
                null,
                "public"
            );

            when(organizationRepository.findByNativeIdAndProviderId(42L, PROVIDER_ID)).thenReturn(Optional.empty());

            Organization result = processor.process(group, PROVIDER_ID);

            assertThat(result).isNull();
            verify(organizationRepository).upsert(any(), any(), any(), any(), any(), any());
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
                "private"
            );

            when(organizationRepository.findByNativeIdAndProviderId(99L, PROVIDER_ID)).thenReturn(
                Optional.of(new Organization())
            );

            processor.process(group, PROVIDER_ID);

            verify(organizationRepository).upsert(
                eq(99L),
                eq(PROVIDER_ID),
                eq("org/team"),
                eq("org/team"), // name falls back to fullPath
                eq(null),
                eq("https://gitlab.com/org/team")
            );
        }

        @Test
        @DisplayName("null response returns null")
        void nullResponse_returnsNull() {
            assertThat(processor.process(null, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).upsert(any(), any(), any(), any(), any(), any());
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
                "public"
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).upsert(any(), any(), any(), any(), any(), any());
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
                "public"
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).upsert(any(), any(), any(), any(), any(), any());
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
                "public"
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).upsert(any(), any(), any(), any(), any(), any());
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
                "public"
            );
            assertThat(processor.process(group, PROVIDER_ID)).isNull();
            verify(organizationRepository, never()).upsert(any(), any(), any(), any(), any(), any());
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
                "internal"
            );

            when(organizationRepository.findByNativeIdAndProviderId(7L, PROVIDER_ID)).thenReturn(
                Optional.of(new Organization())
            );

            processor.process(group, PROVIDER_ID);

            verify(organizationRepository).upsert(
                eq(7L),
                eq(PROVIDER_ID),
                eq("org/team/subteam/deepteam"),
                eq("Deep Team"),
                eq(null),
                eq("https://gitlab.com/org/team/subteam/deepteam")
            );
        }
    }
}
