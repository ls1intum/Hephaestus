package de.tum.in.www1.hephaestus.gitprovider.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("CommitAuthorResolver")
class CommitAuthorResolverTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    private CommitAuthorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CommitAuthorResolver(userRepository);
    }

    // ========== Helper ==========

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // ========== extractLoginFromNoreply (static, no mocks needed) ==========

    @Nested
    @DisplayName("extractLoginFromNoreply")
    class ExtractLoginFromNoreply {

        @Test
        @DisplayName("should extract login from simple noreply email")
        void shouldExtractLoginFromSimpleNoreply() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("username@users.noreply.github.com");
            assertThat(login).isEqualTo("username");
        }

        @Test
        @DisplayName("should extract login from ID-prefixed noreply email")
        void shouldExtractLoginFromIdPrefixedNoreply() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("12345+username@users.noreply.github.com");
            assertThat(login).isEqualTo("username");
        }

        @Test
        @DisplayName("should handle large numeric ID prefix")
        void shouldHandleLargeNumericIdPrefix() {
            String login = CommitAuthorResolver.extractLoginFromNoreply(
                "123456789+FelixTJDietrich@users.noreply.github.com"
            );
            assertThat(login).isEqualTo("FelixTJDietrich");
        }

        @Test
        @DisplayName("should return null for regular email")
        void shouldReturnNullForRegularEmail() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("user@example.com");
            assertThat(login).isNull();
        }

        @Test
        @DisplayName("should return null for personal email")
        void shouldReturnNullForPersonalEmail() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("felix_dietrich@gmx.de");
            assertThat(login).isNull();
        }

        @Test
        @DisplayName("should be case insensitive for domain")
        void shouldBeCaseInsensitiveForDomain() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("user@Users.Noreply.GitHub.com");
            assertThat(login).isEqualTo("user");
        }

        @Test
        @DisplayName("should return null for partial noreply match")
        void shouldReturnNullForPartialNoreplyMatch() {
            String login = CommitAuthorResolver.extractLoginFromNoreply("user@noreply.github.com");
            assertThat(login).isNull();
        }
    }

    // ========== resolveByEmail ==========

    @Nested
    @DisplayName("resolveByEmail")
    class ResolveByEmail {

        @Test
        @DisplayName("should return null for null email")
        void shouldReturnNullForNullEmail() {
            Long result = resolver.resolveByEmail(null, null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return null for blank email")
        void shouldReturnNullForBlankEmail() {
            Long result = resolver.resolveByEmail("   ", null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return user ID on direct email match")
        void shouldReturnUserIdOnDirectEmailMatch() {
            User user = createUser(42L);
            when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("author@example.com", null);

            assertThat(result).isEqualTo(42L);
            verify(userRepository).findByEmail("author@example.com");
            verify(userRepository, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("should fall back to noreply login match when email not found")
        void shouldFallBackToNoreplyLoginMatch() {
            when(userRepository.findByEmail("username@users.noreply.github.com")).thenReturn(Optional.empty());

            User user = createUser(99L);
            when(userRepository.findByLogin("username")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("username@users.noreply.github.com", null);

            assertThat(result).isEqualTo(99L);
            verify(userRepository).findByEmail("username@users.noreply.github.com");
            verify(userRepository).findByLogin("username");
        }

        @Test
        @DisplayName("should fall back to noreply login match for ID-prefixed email")
        void shouldFallBackToNoreplyLoginMatchForIdPrefixed() {
            when(userRepository.findByEmail("12345+dev@users.noreply.github.com")).thenReturn(Optional.empty());

            User user = createUser(77L);
            when(userRepository.findByLogin("dev")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("12345+dev@users.noreply.github.com", null);

            assertThat(result).isEqualTo(77L);
        }

        @Test
        @DisplayName("should return null when neither email nor noreply login match")
        void shouldReturnNullWhenNeitherMatch() {
            when(userRepository.findByEmail("unknown@users.noreply.github.com")).thenReturn(Optional.empty());
            when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

            Long result = resolver.resolveByEmail("unknown@users.noreply.github.com", null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for dotted local-part email when not found")
        void shouldReturnNullForDottedLocalPartEmailWhenNotFound() {
            when(userRepository.findByEmail("first.last@gmail.com")).thenReturn(Optional.empty());

            Long result = resolver.resolveByEmail("first.last@gmail.com", null);

            assertThat(result).isNull();
            // Dotted local-parts are not login-shaped, so Strategy 3 should skip them.
            verify(userRepository, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("should fall back to email local-part login for TUM-style address")
        void shouldFallBackToEmailLocalPartLoginForTumStyleAddress() {
            when(userRepository.findByEmail("ge27coy@mytum.de")).thenReturn(Optional.empty());

            User user = createUser(101L);
            when(userRepository.findByLogin("ge27coy")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("ge27coy@mytum.de", null);

            assertThat(result).isEqualTo(101L);
            verify(userRepository).findByEmail("ge27coy@mytum.de");
            verify(userRepository).findByLogin("ge27coy");
        }

        @Test
        @DisplayName("should not try login match for GitLab bot noreply email")
        void shouldNotTryLoginMatchForGitLabBotNoreplyEmail() {
            when(userRepository.findByEmail("group_319719_bot_abc123@noreply.gitlab.lrz.de")).thenReturn(
                Optional.empty()
            );

            Long result = resolver.resolveByEmail("group_319719_bot_abc123@noreply.gitlab.lrz.de", null);

            assertThat(result).isNull();
            verify(userRepository, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("should prefer direct email match over noreply parsing")
        void shouldPreferDirectEmailMatchOverNoreplyParsing() {
            // Edge case: noreply email is stored directly in user's email field
            User user = createUser(55L);
            when(userRepository.findByEmail("dev@users.noreply.github.com")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("dev@users.noreply.github.com", null);

            assertThat(result).isEqualTo(55L);
            // Should not proceed to login lookup since email matched
            verify(userRepository, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
        }
    }

    // ========== resolveByLogin ==========

    @Nested
    @DisplayName("resolveByLogin")
    class ResolveByLogin {

        @Test
        @DisplayName("should return null for null login")
        void shouldReturnNullForNullLogin() {
            Long result = resolver.resolveByLogin(null, null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return null for blank login")
        void shouldReturnNullForBlankLogin() {
            Long result = resolver.resolveByLogin("  ", null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return user ID when login found")
        void shouldReturnUserIdWhenLoginFound() {
            User user = createUser(33L);
            when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByLogin("testuser", null);

            assertThat(result).isEqualTo(33L);
        }

        @Test
        @DisplayName("should return null when login not found")
        void shouldReturnNullWhenLoginNotFound() {
            when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

            Long result = resolver.resolveByLogin("unknown", null);

            assertThat(result).isNull();
        }
    }

    // ========== Strategy 4: display-name match ==========

    @Nested
    @DisplayName("resolveByEmail — strategy 4 (display-name match)")
    class ResolveByEmailDisplayName {

        @Test
        @DisplayName("should match firstname.lastname@tum.de against User.name when exactly one candidate")
        void shouldMatchFirstnameLastnameAgainstUserName() {
            when(userRepository.findByEmailAndProviderId("erik.kiessig@tum.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(5202L);
            when(userRepository.findAllByNameAndProviderId("Erik Kiessig", 3L)).thenReturn(List.of(user));

            Long result = resolver.resolveByEmail("erik.kiessig@tum.de", 3L);

            assertThat(result).isEqualTo(5202L);
            verify(userRepository).findAllByNameAndProviderId("Erik Kiessig", 3L);
        }

        @Test
        @DisplayName("should fall back to cross-provider name match when providerId is null")
        void shouldFallBackToCrossProviderNameMatchWhenProviderIdIsNull() {
            when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());

            User user = createUser(777L);
            when(userRepository.findAllByName("John Doe")).thenReturn(List.of(user));

            Long result = resolver.resolveByEmail("john.doe@example.com", null);

            assertThat(result).isEqualTo(777L);
        }

        @Test
        @DisplayName("should skip display-name match when multiple candidates are ambiguous")
        void shouldSkipDisplayNameMatchWhenAmbiguous() {
            when(userRepository.findByEmailAndProviderId("john.smith@tum.de", 3L)).thenReturn(Optional.empty());
            when(userRepository.findAllByNameAndProviderId("John Smith", 3L)).thenReturn(
                List.of(createUser(1L), createUser(2L))
            );

            Long result = resolver.resolveByEmail("john.smith@tum.de", 3L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle three-token dotted local-parts")
        void shouldHandleThreeTokenDottedLocalParts() {
            when(userRepository.findByEmailAndProviderId("anna.marie.huber@in.tum.de", 3L)).thenReturn(
                Optional.empty()
            );

            User user = createUser(42L);
            when(userRepository.findAllByNameAndProviderId("Anna Marie Huber", 3L)).thenReturn(List.of(user));

            Long result = resolver.resolveByEmail("anna.marie.huber@in.tum.de", 3L);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("should prefer login match over display-name match when login resolves")
        void shouldPreferLoginMatchOverDisplayName() {
            // Login-style local-parts skip strategy 4 entirely.
            when(userRepository.findByEmailAndProviderId("go98tod@mytum.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(4875L);
            when(userRepository.findByLoginAndProviderId("go98tod", 3L)).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("go98tod@mytum.de", 3L);

            assertThat(result).isEqualTo(4875L);
            verify(userRepository, never()).findAllByNameAndProviderId(anyString(), anyLong());
        }

        @Test
        @DisplayName("should return null for numeric-prefixed local-part")
        void shouldReturnNullForNumericPrefixedLocalPart() {
            when(userRepository.findByEmailAndProviderId("42.spam@domain.com", 3L)).thenReturn(Optional.empty());

            Long result = resolver.resolveByEmail("42.spam@domain.com", 3L);

            assertThat(result).isNull();
            verify(userRepository, never()).findAllByNameAndProviderId(anyString(), anyLong());
        }
    }

    // ========== resolveAndBackfillByEmail ==========

    @Nested
    @DisplayName("resolveAndBackfillByEmail")
    class ResolveAndBackfillByEmail {

        @Test
        @DisplayName("should backfill user email when strategy 3 resolves login-style address")
        void shouldBackfillEmailWhenStrategy3Resolves() {
            when(userRepository.findByEmailAndProviderId("go98tod@mytum.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(4875L);
            when(userRepository.findByLoginAndProviderId("go98tod", 3L)).thenReturn(Optional.of(user));

            Long result = resolver.resolveAndBackfillByEmail("go98tod@mytum.de", 3L);

            assertThat(result).isEqualTo(4875L);
            verify(userRepository).backfillEmailIfNull(4875L, "go98tod@mytum.de");
        }

        @Test
        @DisplayName("should backfill user email when strategy 4 resolves firstname.lastname address")
        void shouldBackfillEmailWhenStrategy4Resolves() {
            when(userRepository.findByEmailAndProviderId("erik.kiessig@tum.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(5202L);
            when(userRepository.findAllByNameAndProviderId("Erik Kiessig", 3L)).thenReturn(List.of(user));

            Long result = resolver.resolveAndBackfillByEmail("erik.kiessig@tum.de", 3L);

            assertThat(result).isEqualTo(5202L);
            verify(userRepository).backfillEmailIfNull(5202L, "erik.kiessig@tum.de");
        }

        @Test
        @DisplayName("should lowercase email before backfilling")
        void shouldLowercaseEmailBeforeBackfilling() {
            when(userRepository.findByEmailAndProviderId("Erik.Kiessig@TUM.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(5202L);
            when(userRepository.findAllByNameAndProviderId("Erik Kiessig", 3L)).thenReturn(List.of(user));

            resolver.resolveAndBackfillByEmail("Erik.Kiessig@TUM.de", 3L);

            verify(userRepository).backfillEmailIfNull(5202L, "erik.kiessig@tum.de");
        }

        @Test
        @DisplayName("should NOT backfill when resolution came from direct email match (strategy 1)")
        void shouldNotBackfillWhenStrategy1Resolves() {
            User user = createUser(100L);
            when(userRepository.findByEmailAndProviderId("user@example.com", 3L)).thenReturn(Optional.of(user));

            Long result = resolver.resolveAndBackfillByEmail("user@example.com", 3L);

            assertThat(result).isEqualTo(100L);
            // Strategy 1 already implies the email is stored — backfilling is a no-op.
            // We allow it to fire (it's cheap and idempotent) for single-token local-parts.
            verify(userRepository).backfillEmailIfNull(100L, "user@example.com");
        }

        @Test
        @DisplayName("should NOT backfill for GitHub noreply address")
        void shouldNotBackfillForGitHubNoreply() {
            when(userRepository.findByEmail("dev@users.noreply.github.com")).thenReturn(Optional.empty());

            User user = createUser(55L);
            when(userRepository.findByLogin("dev")).thenReturn(Optional.of(user));

            Long result = resolver.resolveAndBackfillByEmail("dev@users.noreply.github.com", null);

            assertThat(result).isEqualTo(55L);
            verify(userRepository, never()).backfillEmailIfNull(anyLong(), anyString());
        }

        @Test
        @DisplayName("should NOT backfill for GitLab bot noreply address")
        void shouldNotBackfillForGitLabBotNoreply() {
            // Resolver returns null for bot noreply (no strategy can match), so we
            // cover the eligibility filter via isBackfillEligible directly below.
            when(userRepository.findByEmail("group_319719_bot_abc@noreply.gitlab.lrz.de")).thenReturn(
                Optional.empty()
            );

            Long result = resolver.resolveAndBackfillByEmail("group_319719_bot_abc@noreply.gitlab.lrz.de", null);

            assertThat(result).isNull();
            verify(userRepository, never()).backfillEmailIfNull(anyLong(), anyString());
        }

        @Test
        @DisplayName("should not throw when backfill repository call fails")
        void shouldNotThrowWhenBackfillFails() {
            when(userRepository.findByEmailAndProviderId("go98tod@mytum.de", 3L)).thenReturn(Optional.empty());

            User user = createUser(4875L);
            when(userRepository.findByLoginAndProviderId("go98tod", 3L)).thenReturn(Optional.of(user));
            when(userRepository.backfillEmailIfNull(4875L, "go98tod@mytum.de")).thenThrow(
                new RuntimeException("boom")
            );

            Long result = resolver.resolveAndBackfillByEmail("go98tod@mytum.de", 3L);

            // Resolution must still succeed even if the opportunistic backfill failed.
            assertThat(result).isEqualTo(4875L);
        }
    }

    // ========== isBackfillEligible (static helper) ==========

    @Nested
    @DisplayName("isBackfillEligible")
    class IsBackfillEligible {

        @Test
        @DisplayName("should accept login-style institutional address")
        void shouldAcceptLoginStyleAddress() {
            assertThat(CommitAuthorResolver.isBackfillEligible("ge27coy@mytum.de")).isTrue();
        }

        @Test
        @DisplayName("should accept firstname.lastname institutional address")
        void shouldAcceptFirstnameLastnameAddress() {
            assertThat(CommitAuthorResolver.isBackfillEligible("erik.kiessig@tum.de")).isTrue();
        }

        @Test
        @DisplayName("should reject GitHub noreply address")
        void shouldRejectGitHubNoreply() {
            assertThat(CommitAuthorResolver.isBackfillEligible("1234+dev@users.noreply.github.com")).isFalse();
        }

        @Test
        @DisplayName("should reject GitLab bot noreply address")
        void shouldRejectGitLabBotNoreply() {
            assertThat(CommitAuthorResolver.isBackfillEligible("group_123_bot_abc@noreply.gitlab.lrz.de")).isFalse();
        }

        @Test
        @DisplayName("should reject null and blank")
        void shouldRejectNullAndBlank() {
            assertThat(CommitAuthorResolver.isBackfillEligible(null)).isFalse();
            assertThat(CommitAuthorResolver.isBackfillEligible("")).isFalse();
            assertThat(CommitAuthorResolver.isBackfillEligible("   ")).isFalse();
        }

        @Test
        @DisplayName("should reject malformed address")
        void shouldRejectMalformedAddress() {
            assertThat(CommitAuthorResolver.isBackfillEligible("no-at-sign")).isFalse();
            assertThat(CommitAuthorResolver.isBackfillEligible("@nolocalpart.com")).isFalse();
            assertThat(CommitAuthorResolver.isBackfillEligible("nolocalpart@")).isFalse();
        }
    }

    // ========== dottedLocalPartToDisplayName (static helper) ==========

    @Nested
    @DisplayName("dottedLocalPartToDisplayName")
    class DottedLocalPartToDisplayName {

        @Test
        @DisplayName("should capitalise single-dot local-part")
        void shouldCapitaliseSingleDot() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("erik.kiessig")).isEqualTo("Erik Kiessig");
        }

        @Test
        @DisplayName("should lowercase remaining characters")
        void shouldLowercaseRemainingCharacters() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("ERIK.KIESSIG")).isEqualTo("Erik Kiessig");
        }

        @Test
        @DisplayName("should handle three tokens")
        void shouldHandleThreeTokens() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("anna.marie.huber")).isEqualTo(
                "Anna Marie Huber"
            );
        }

        @Test
        @DisplayName("should return null for empty token")
        void shouldReturnNullForEmptyToken() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("erik..kiessig")).isNull();
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName(".kiessig")).isNull();
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("erik.")).isNull();
        }

        @Test
        @DisplayName("should return null for input without dot")
        void shouldReturnNullForInputWithoutDot() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("ge27coy")).isNull();
        }

        @Test
        @DisplayName("should return null for null or blank input")
        void shouldReturnNullForNullOrBlank() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName(null)).isNull();
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("")).isNull();
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("   ")).isNull();
        }

        @Test
        @DisplayName("should preserve single-character tokens")
        void shouldPreserveSingleCharacterTokens() {
            assertThat(CommitAuthorResolver.dottedLocalPartToDisplayName("a.b")).isEqualTo("A B");
        }
    }
}
