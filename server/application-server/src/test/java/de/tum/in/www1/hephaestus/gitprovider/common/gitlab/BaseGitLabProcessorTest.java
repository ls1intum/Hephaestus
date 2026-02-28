package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

@DisplayName("BaseGitLabProcessor")
class BaseGitLabProcessorTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ScopeIdResolver scopeIdResolver;

    @Mock
    private RepositoryScopeFilter repositoryScopeFilter;

    private TestProcessor processor;
    private Repository testRepo;

    @BeforeEach
    void setUp() {
        GitLabProperties properties = new GitLabProperties(
            "https://gitlab.lrz.de",
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        processor = new TestProcessor(
            userRepository,
            labelRepository,
            repositoryRepository,
            scopeIdResolver,
            repositoryScopeFilter,
            properties
        );

        testRepo = new Repository();
        testRepo.setId(-246765L);
        testRepo.setNameWithOwner("hephaestustest/demo-repository");
    }

    // ========================================================================
    // Timestamp Parsing
    // ========================================================================

    @Nested
    @DisplayName("Timestamp parsing")
    class TimestampParsing {

        @Test
        @DisplayName("parses ISO-8601 format (GraphQL)")
        void parsesIso8601() {
            Instant result = processor.callParseGitLabTimestamp("2026-01-31T18:03:35Z");

            assertThat(result).isNotNull();
            assertThat(result.toString()).isEqualTo("2026-01-31T18:03:35Z");
        }

        @Test
        @DisplayName("parses ISO-8601 with offset")
        void parsesIso8601WithOffset() {
            Instant result = processor.callParseGitLabTimestamp("2026-01-31T19:03:35+01:00");

            assertThat(result).isNotNull();
            assertThat(result.toString()).isEqualTo("2026-01-31T18:03:35Z");
        }

        @Test
        @DisplayName("parses webhook format (yyyy-MM-dd HH:mm:ss +ZZZZ)")
        void parsesWebhookFormat() {
            Instant result = processor.callParseGitLabTimestamp("2026-01-31 19:03:35 +0100");

            assertThat(result).isNotNull();
            assertThat(result.toString()).isEqualTo("2026-01-31T18:03:35Z");
        }

        @Test
        @DisplayName("parses webhook format without timezone")
        void parsesWebhookFormatNoTimezone() {
            Instant result = processor.callParseGitLabTimestamp("2026-01-31 19:03:35");

            assertThat(result).isNotNull();
            // Defaults to UTC offset 0
            assertThat(result.toString()).isEqualTo("2026-01-31T19:03:35Z");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "  ", "\t" })
        @DisplayName("returns null for null/blank timestamps")
        void returnsNullForNullOrBlank(String input) {
            Instant result = processor.callParseGitLabTimestamp(input);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for unparseable timestamp")
        void returnsNullForUnparseable() {
            Instant result = processor.callParseGitLabTimestamp("not-a-timestamp");
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // User Resolution
    // ========================================================================

    @Nested
    @DisplayName("User resolution (webhook)")
    class WebhookUserResolution {

        @Test
        @DisplayName("finds or creates user from webhook data")
        void findsOrCreatesUserFromWebhook() {
            User user = new User();
            user.setId(-18024L);
            user.setLogin("ga84xah");

            when(userRepository.findByNativeIdAndProviderId(18024L, 1L)).thenReturn(Optional.of(user));

            GitLabWebhookUser dto = new GitLabWebhookUser(
                18024L,
                "ga84xah",
                "Felix Dietrich",
                "https://avatar.url",
                null
            );

            User result = processor.callFindOrCreateUser(dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(-18024L);
            assertThat(result.getLogin()).isEqualTo("ga84xah");
        }

        @Test
        @DisplayName("returns null for null webhook user")
        void returnsNullForNullUser() {
            User result = processor.callFindOrCreateUser((GitLabWebhookUser) null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for user with null id")
        void returnsNullForNullUserId() {
            GitLabWebhookUser dto = new GitLabWebhookUser(null, "ga84xah", "Felix", null, null);
            User result = processor.callFindOrCreateUser(dto);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for user with null username")
        void returnsNullForNullUsername() {
            GitLabWebhookUser dto = new GitLabWebhookUser(18024L, null, "Felix", null, null);
            User result = processor.callFindOrCreateUser(dto);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("User resolution (GraphQL)")
    class GraphQLUserResolution {

        @Test
        @DisplayName("finds or creates user from GraphQL data")
        void findsOrCreatesUserFromGraphQL() {
            User user = new User();
            user.setId(-18024L);

            when(userRepository.findByNativeIdAndProviderId(18024L, 1L)).thenReturn(Optional.of(user));

            User result = processor.callFindOrCreateUser(
                "gid://gitlab/User/18024",
                "ga84xah",
                "Felix Dietrich",
                "https://avatar.url",
                "https://gitlab.lrz.de/ga84xah"
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(-18024L);
        }

        @Test
        @DisplayName("returns null for invalid globalId")
        void returnsNullForInvalidGlobalId() {
            User result = processor.callFindOrCreateUser("invalid", "ga84xah", "Felix", null, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for null globalId")
        void returnsNullForNullGlobalId() {
            User result = processor.callFindOrCreateUser(null, "ga84xah", "Felix", null, null);
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // Label Resolution
    // ========================================================================

    @Nested
    @DisplayName("Label resolution")
    class LabelResolution {

        @Test
        @DisplayName("returns existing label by name")
        void returnsExistingLabel() {
            Label existing = new Label();
            existing.setId(-85907L);
            existing.setName("enhancement");

            when(labelRepository.findByRepositoryIdAndName(testRepo.getId(), "enhancement")).thenReturn(
                Optional.of(existing)
            );

            GitLabWebhookLabel dto = new GitLabWebhookLabel(85907L, "enhancement", "#a2eeef");
            Label result = processor.callFindOrCreateLabel(dto, testRepo);

            assertThat(result).isSameAs(existing);
            verify(labelRepository, never()).insertIfAbsent(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("creates new label with native ID")
        void createsNewLabelWithNativeId() {
            when(labelRepository.findByRepositoryIdAndName(testRepo.getId(), "enhancement")).thenReturn(
                Optional.empty()
            );

            Label created = new Label();
            created.setId(85907L);
            created.setName("enhancement");

            when(labelRepository.insertIfAbsent(85907L, "enhancement", "#a2eeef", testRepo.getId())).thenReturn(1);
            when(labelRepository.findById(85907L)).thenReturn(Optional.of(created));

            GitLabWebhookLabel dto = new GitLabWebhookLabel(85907L, "enhancement", "#a2eeef");
            Label result = processor.callFindOrCreateLabel(dto, testRepo);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(85907L);
        }

        @Test
        @DisplayName("returns null for null label")
        void returnsNullForNullLabel() {
            Label result = processor.callFindOrCreateLabel((GitLabWebhookLabel) null, testRepo);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for label with blank title")
        void returnsNullForBlankTitle() {
            GitLabWebhookLabel dto = new GitLabWebhookLabel(85907L, "  ", "#a2eeef");
            Label result = processor.callFindOrCreateLabel(dto, testRepo);
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // Context Resolution
    // ========================================================================

    @Nested
    @DisplayName("Context resolution")
    class ContextResolution {

        @Test
        @DisplayName("returns null when repository is filtered")
        void returnsNullWhenFiltered() {
            when(repositoryScopeFilter.isRepositoryAllowed("org/repo")).thenReturn(false);
            var ctx = processor.callResolveContext("org/repo", "open");
            assertThat(ctx).isNull();
        }

        @Test
        @DisplayName("returns null when repository not found")
        void returnsNullWhenNotFound() {
            when(repositoryScopeFilter.isRepositoryAllowed("org/repo")).thenReturn(true);
            when(repositoryRepository.findByNameWithOwnerWithOrganization("org/repo")).thenReturn(Optional.empty());
            var ctx = processor.callResolveContext("org/repo", "open");
            assertThat(ctx).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "  " })
        @DisplayName("returns null for null/blank path")
        void returnsNullForNullOrBlankPath(String path) {
            var ctx = processor.callResolveContext(path, "open");
            assertThat(ctx).isNull();
        }
    }

    // ========================================================================
    // ID Negation (delegated to GitLabSyncConstants)
    // ========================================================================

    @Nested
    @DisplayName("ID mapping")
    class IdMapping {

        @ParameterizedTest(name = "toEntityId({0}) = {1}")
        @CsvSource({ "1, 1", "42, 42", "18024, 18024", "422296, 422296" })
        @DisplayName("returns raw GitLab IDs as entity IDs")
        void returnsRawIds(long rawId, long expectedEntityId) {
            assertThat(GitLabSyncConstants.toEntityId(rawId)).isEqualTo(expectedEntityId);
        }

        @ParameterizedTest(name = "extractEntityId(\"{0}\") = {1}")
        @CsvSource(
            {
                "gid://gitlab/User/18024, 18024",
                "gid://gitlab/Issue/422296, 422296",
                "gid://gitlab/Project/246765, 246765",
                "gid://gitlab/Label/85907, 85907",
            }
        )
        @DisplayName("extracts numeric IDs from global IDs")
        void extractsNumericIds(String globalId, long expectedEntityId) {
            assertThat(GitLabSyncConstants.extractEntityId(globalId)).isEqualTo(expectedEntityId);
        }
    }

    // ========================================================================
    // Test Processor (exposes protected methods)
    // ========================================================================

    private static class TestProcessor extends BaseGitLabProcessor {

        TestProcessor(
            UserRepository userRepository,
            LabelRepository labelRepository,
            RepositoryRepository repositoryRepository,
            ScopeIdResolver scopeIdResolver,
            RepositoryScopeFilter repositoryScopeFilter,
            GitLabProperties gitLabProperties
        ) {
            super(
                userRepository,
                labelRepository,
                repositoryRepository,
                scopeIdResolver,
                repositoryScopeFilter,
                gitLabProperties
            );
        }

        Instant callParseGitLabTimestamp(String timestamp) {
            return parseGitLabTimestamp(timestamp);
        }

        User callFindOrCreateUser(GitLabWebhookUser dto) {
            return findOrCreateUser(dto, 1L);
        }

        User callFindOrCreateUser(String globalId, String username, String name, String avatarUrl, String webUrl) {
            return findOrCreateUser(globalId, username, name, avatarUrl, webUrl, 1L);
        }

        Label callFindOrCreateLabel(GitLabWebhookLabel dto, Repository repository) {
            return findOrCreateLabel(dto, repository);
        }

        de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext callResolveContext(String path, String action) {
            return resolveContext(path, action);
        }
    }
}
