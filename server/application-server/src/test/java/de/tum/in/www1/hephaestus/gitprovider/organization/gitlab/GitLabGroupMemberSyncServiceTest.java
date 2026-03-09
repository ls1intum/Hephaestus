package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse.GitLabAccessLevel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupMemberResponse.GitLabMemberUser;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.OrganizationSyncedEvent;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;

@Tag("unit")
@DisplayName("GitLabGroupMemberSyncService")
class GitLabGroupMemberSyncServiceTest extends BaseUnitTest {

    private static final Long TEST_PROVIDER_ID = 100L;
    private static final Long SCOPE_ID = 1L;

    @Mock
    private GitLabGraphQlClientProvider graphQlClientProvider;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    @Mock
    private OrganizationMembershipListener organizationMembershipListener;

    private final GitLabProperties gitLabProperties = new GitLabProperties(
        "https://gitlab.com",
        Duration.ofSeconds(30),
        Duration.ofSeconds(60),
        Duration.ofMillis(10), // fast throttle for tests
        Duration.ofMinutes(5)
    );

    private GitLabGroupMemberSyncService service;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        GitProvider gitLabProvider = mock(GitProvider.class);
        lenient().when(gitLabProvider.getId()).thenReturn(TEST_PROVIDER_ID);
        lenient()
            .when(gitProviderRepository.findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com"))
            .thenReturn(Optional.of(gitLabProvider));
        lenient().when(gitProviderRepository.getReferenceById(TEST_PROVIDER_ID)).thenReturn(gitLabProvider);

        // Default: save returns its argument with an ID set
        lenient()
            .when(userRepository.save(any(User.class)))
            .thenAnswer(inv -> {
                User u = inv.getArgument(0);
                if (u.getId() == null) {
                    u.setId(u.getNativeId() + 1000L); // deterministic test ID
                }
                return u;
            });

        service = new GitLabGroupMemberSyncService(
            graphQlClientProvider,
            organizationRepository,
            organizationMembershipRepository,
            userRepository,
            gitProviderRepository,
            gitLabProperties,
            organizationMembershipListener
        );

        testOrg = new Organization();
        testOrg.setId(42L);
        testOrg.setLogin("my-org");
    }

    @Nested
    @DisplayName("syncGroupMemberships")
    class SyncGroupMemberships {

        @Test
        @DisplayName("null organization returns -1")
        void nullOrganization_returnsNegative() {
            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", null);
            assertThat(result).isEqualTo(-1);
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("null group path returns -1")
        void nullGroupPath_returnsNegative() {
            int result = service.syncGroupMemberships(SCOPE_ID, null, testOrg);
            assertThat(result).isEqualTo(-1);
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("blank group path returns -1")
        void blankGroupPath_returnsNegative() {
            int result = service.syncGroupMemberships(SCOPE_ID, "   ", testOrg);
            assertThat(result).isEqualTo(-1);
            verify(graphQlClientProvider, never()).acquirePermission();
        }

        @Test
        @DisplayName("single page syncs all members and fires event")
        void singlePage_syncsAllMembers() {
            var member1 = createMember("gid://gitlab/User/10", "alice", "Alice", 30); // DEVELOPER → MEMBER
            var member2 = createMember("gid://gitlab/User/20", "bob", "Bob", 50); // OWNER → ADMIN

            ClientGraphQlResponse response = mockMembersPage(List.of(member1, member2), null);
            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, response);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);
            when(userRepository.findByNativeIdAndProviderId(10L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());
            when(userRepository.findByNativeIdAndProviderId(20L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of());

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(2);
            verify(organizationMembershipRepository).upsertMembership(
                eq(42L),
                anyLong(),
                eq(OrganizationMemberRole.MEMBER)
            );
            verify(organizationMembershipRepository).upsertMembership(
                eq(42L),
                anyLong(),
                eq(OrganizationMemberRole.ADMIN)
            );
            verify(organizationMembershipListener).onOrganizationMembershipsSynced(any(OrganizationSyncedEvent.class));
        }

        @Test
        @DisplayName("multi-page pagination fetches all members")
        void multiPage_fetchesAllMembers() {
            var member1 = createMember("gid://gitlab/User/10", "alice", "Alice", 30);
            var member2 = createMember("gid://gitlab/User/20", "bob", "Bob", 40);

            ClientGraphQlResponse page1 = mockMembersPage(List.of(member1), new GitLabPageInfo(true, "cursor1"));
            ClientGraphQlResponse page2 = mockMembersPage(List.of(member2), new GitLabPageInfo(false, null));

            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, page1, page2);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);
            when(userRepository.findByNativeIdAndProviderId(anyLong(), eq(TEST_PROVIDER_ID))).thenReturn(
                Optional.empty()
            );
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of());

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("stale members are removed after complete sync")
        void staleMembersRemoved() {
            var member1 = createMember("gid://gitlab/User/10", "alice", "Alice", 30);

            ClientGraphQlResponse response = mockMembersPage(List.of(member1), null);
            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, response);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);

            User existingUser = new User();
            existingUser.setId(1010L);
            existingUser.setNativeId(10L);
            existingUser.setLogin("alice");
            existingUser.setAvatarUrl("");
            existingUser.setHtmlUrl("https://gitlab.com/alice");
            existingUser.setType(User.Type.USER);
            when(userRepository.findByNativeIdAndProviderId(10L, TEST_PROVIDER_ID)).thenReturn(
                Optional.of(existingUser)
            );
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            // Existing memberships include a stale user (id=9999)
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of(1010L, 9999L));

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(1);
            // Verify stale user 9999 was removed
            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Collection<Long>> staleCaptor = ArgumentCaptor.forClass(
                java.util.Collection.class
            );
            verify(organizationMembershipRepository).deleteByOrganizationIdAndUserIdIn(eq(42L), staleCaptor.capture());
            assertThat(staleCaptor.getValue()).containsExactly(9999L);
        }

        @Test
        @DisplayName("stale removal skipped when sync incomplete")
        void staleRemovalSkipped_whenIncomplete() {
            // Simulate API failure on first page
            HttpGraphQlClient client = mockClient();
            ClientGraphQlResponse invalidResp = mock(ClientGraphQlResponse.class);
            when(invalidResp.isValid()).thenReturn(false);
            when(invalidResp.getErrors()).thenReturn(List.of());

            HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
            when(client.documentName(anyString())).thenReturn(requestSpec);
            when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);
            when(requestSpec.execute()).thenReturn(Mono.just(invalidResp));
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            // Sync failed, so stale removal must not happen
            verify(organizationMembershipRepository, never()).deleteByOrganizationIdAndUserIdIn(anyLong(), any());
            // Event still fires (even on partial sync, so downstream can reconcile)
            verify(organizationMembershipListener).onOrganizationMembershipsSynced(any());
        }

        @Test
        @DisplayName("null member user is skipped")
        void nullMemberUser_skipped() {
            var nullUserMember = new GitLabGroupMemberResponse(null, new GitLabAccessLevel("DEVELOPER", 30));
            var validMember = createMember("gid://gitlab/User/10", "alice", "Alice", 30);

            ClientGraphQlResponse response = mockMembersPage(List.of(nullUserMember, validMember), null);
            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, response);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);
            when(userRepository.findByNativeIdAndProviderId(10L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of());

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("invalid GID is skipped gracefully")
        void invalidGid_skippedGracefully() {
            var badMember = createMember("invalid-gid", "baduser", "Bad User", 30);
            var goodMember = createMember("gid://gitlab/User/10", "alice", "Alice", 30);

            ClientGraphQlResponse response = mockMembersPage(List.of(badMember, goodMember), null);
            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, response);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);
            when(userRepository.findByNativeIdAndProviderId(10L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of());

            int result = service.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("no listener configured does not throw")
        void noListener_doesNotThrow() {
            var serviceNoListener = new GitLabGroupMemberSyncService(
                graphQlClientProvider,
                organizationRepository,
                organizationMembershipRepository,
                userRepository,
                gitProviderRepository,
                gitLabProperties,
                null // no listener
            );

            var member = createMember("gid://gitlab/User/10", "alice", "Alice", 30);
            ClientGraphQlResponse response = mockMembersPage(List.of(member), null);
            HttpGraphQlClient client = mockClient();
            mockSequentialExecute(client, response);
            when(graphQlClientProvider.getRateLimitRemaining(SCOPE_ID)).thenReturn(100);
            when(userRepository.findByNativeIdAndProviderId(10L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());
            when(organizationMembershipRepository.findUserIdsByOrganizationId(42L)).thenReturn(List.of());

            int result = serviceNoListener.syncGroupMemberships(SCOPE_ID, "my-org", testOrg);

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("mapAccessLevel")
    class MapAccessLevel {

        @Test
        @DisplayName("OWNER (50) maps to ADMIN")
        void owner_mapsToAdmin() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("OWNER", 50))).isEqualTo(
                OrganizationMemberRole.ADMIN
            );
        }

        @Test
        @DisplayName("MAINTAINER (40) maps to ADMIN")
        void maintainer_mapsToAdmin() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("MAINTAINER", 40))).isEqualTo(
                OrganizationMemberRole.ADMIN
            );
        }

        @Test
        @DisplayName("DEVELOPER (30) maps to MEMBER")
        void developer_mapsToMember() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("DEVELOPER", 30))).isEqualTo(
                OrganizationMemberRole.MEMBER
            );
        }

        @Test
        @DisplayName("REPORTER (20) maps to MEMBER")
        void reporter_mapsToMember() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("REPORTER", 20))).isEqualTo(
                OrganizationMemberRole.MEMBER
            );
        }

        @Test
        @DisplayName("GUEST (10) maps to MEMBER")
        void guest_mapsToMember() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("GUEST", 10))).isEqualTo(
                OrganizationMemberRole.MEMBER
            );
        }

        @Test
        @DisplayName("null access level maps to MEMBER")
        void nullAccessLevel_mapsToMember() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(null)).isEqualTo(OrganizationMemberRole.MEMBER);
        }

        @Test
        @DisplayName("null integer value maps to MEMBER")
        void nullIntegerValue_mapsToMember() {
            assertThat(GitLabGroupMemberSyncService.mapAccessLevel(new GitLabAccessLevel("UNKNOWN", null))).isEqualTo(
                OrganizationMemberRole.MEMBER
            );
        }
    }

    @Nested
    @DisplayName("ensureUserExists")
    class EnsureUserExists {

        @Test
        @DisplayName("creates new user when not found")
        void createsNewUser() {
            when(userRepository.findByNativeIdAndProviderId(42L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());

            var memberUser = new GitLabMemberUser(
                "gid://gitlab/User/42",
                "alice",
                "Alice A.",
                "https://gitlab.com/avatar.png",
                "https://gitlab.com/alice"
            );

            User result = service.ensureUserExists(memberUser, TEST_PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getNativeId()).isEqualTo(42L);
            assertThat(result.getLogin()).isEqualTo("alice");
            assertThat(result.getName()).isEqualTo("Alice A.");
            assertThat(result.getAvatarUrl()).isEqualTo("https://gitlab.com/avatar.png");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/alice");
            assertThat(result.getType()).isEqualTo(User.Type.USER);
            assertThat(result.getCreatedAt()).isNotNull();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("updates existing user fields")
        void updatesExistingUser() {
            User existing = new User();
            existing.setId(999L);
            existing.setNativeId(42L);
            existing.setLogin("old-login");
            existing.setAvatarUrl("old-avatar");
            existing.setHtmlUrl("old-url");
            existing.setType(User.Type.USER);
            when(userRepository.findByNativeIdAndProviderId(42L, TEST_PROVIDER_ID)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenReturn(existing);

            var memberUser = new GitLabMemberUser(
                "gid://gitlab/User/42",
                "new-login",
                "New Name",
                "https://new-avatar.png",
                "https://gitlab.com/new-login"
            );

            User result = service.ensureUserExists(memberUser, TEST_PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getLogin()).isEqualTo("new-login");
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getAvatarUrl()).isEqualTo("https://new-avatar.png");
            assertThat(result.getHtmlUrl()).isEqualTo("https://gitlab.com/new-login");
        }

        @Test
        @DisplayName("null avatar URL defaults to empty string")
        void nullAvatarUrl_defaultsToEmpty() {
            when(userRepository.findByNativeIdAndProviderId(42L, TEST_PROVIDER_ID)).thenReturn(Optional.empty());

            var memberUser = new GitLabMemberUser(
                "gid://gitlab/User/42",
                "alice",
                null,
                null, // null avatar
                "https://gitlab.com/alice"
            );

            User result = service.ensureUserExists(memberUser, TEST_PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getAvatarUrl()).isEmpty();
        }

        @Test
        @DisplayName("invalid GID returns null")
        void invalidGid_returnsNull() {
            var memberUser = new GitLabMemberUser(
                "not-a-valid-gid",
                "alice",
                "Alice",
                null,
                "https://gitlab.com/alice"
            );

            User result = service.ensureUserExists(memberUser, TEST_PROVIDER_ID);

            assertThat(result).isNull();
            verify(userRepository, never()).save(any());
        }
    }

    // -- Helpers --

    private GitLabGroupMemberResponse createMember(String gid, String username, String name, int accessLevel) {
        return new GitLabGroupMemberResponse(
            new GitLabMemberUser(gid, username, name, null, "https://gitlab.com/" + username),
            new GitLabAccessLevel(accessLevelName(accessLevel), accessLevel)
        );
    }

    private static String accessLevelName(int level) {
        return switch (level) {
            case 10 -> "GUEST";
            case 20 -> "REPORTER";
            case 30 -> "DEVELOPER";
            case 40 -> "MAINTAINER";
            case 50 -> "OWNER";
            default -> "UNKNOWN";
        };
    }

    private HttpGraphQlClient mockClient() {
        HttpGraphQlClient client = mock(HttpGraphQlClient.class);
        when(graphQlClientProvider.forScope(any())).thenReturn(client);
        return client;
    }

    @SuppressWarnings("unchecked")
    private ClientGraphQlResponse mockMembersPage(List<GitLabGroupMemberResponse> members, GitLabPageInfo pageInfo) {
        ClientGraphQlResponse resp = mock(ClientGraphQlResponse.class);
        when(resp.isValid()).thenReturn(true);

        ClientResponseField nodesField = mock(ClientResponseField.class);
        when(nodesField.<GitLabGroupMemberResponse>toEntityList(any(Class.class))).thenReturn(members);
        when(resp.field("group.groupMembers.nodes")).thenReturn(nodesField);

        ClientResponseField pageInfoField = mock(ClientResponseField.class);
        when(pageInfoField.<GitLabPageInfo>toEntity(any(Class.class))).thenReturn(pageInfo);
        when(resp.field("group.groupMembers.pageInfo")).thenReturn(pageInfoField);

        return resp;
    }

    @SafeVarargs
    private void mockSequentialExecute(
        HttpGraphQlClient client,
        ClientGraphQlResponse first,
        ClientGraphQlResponse... rest
    ) {
        HttpGraphQlClient.RequestSpec requestSpec = mock(HttpGraphQlClient.RequestSpec.class);
        when(client.documentName(anyString())).thenReturn(requestSpec);
        when(requestSpec.variable(anyString(), any())).thenReturn(requestSpec);

        @SuppressWarnings("unchecked")
        Mono<ClientGraphQlResponse>[] restMonos = new Mono[rest.length];
        for (int i = 0; i < rest.length; i++) {
            restMonos[i] = Mono.just(rest[i]);
        }
        when(requestSpec.execute()).thenReturn(Mono.just(first), restMonos);
    }
}
