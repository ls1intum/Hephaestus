package de.tum.in.www1.hephaestus.gitprovider.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
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
            Long result = resolver.resolveByEmail(null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return null for blank email")
        void shouldReturnNullForBlankEmail() {
            Long result = resolver.resolveByEmail("   ");
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return user ID on direct email match")
        void shouldReturnUserIdOnDirectEmailMatch() {
            User user = createUser(42L);
            when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("author@example.com");

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

            Long result = resolver.resolveByEmail("username@users.noreply.github.com");

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

            Long result = resolver.resolveByEmail("12345+dev@users.noreply.github.com");

            assertThat(result).isEqualTo(77L);
        }

        @Test
        @DisplayName("should return null when neither email nor noreply login match")
        void shouldReturnNullWhenNeitherMatch() {
            when(userRepository.findByEmail("unknown@users.noreply.github.com")).thenReturn(Optional.empty());
            when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

            Long result = resolver.resolveByEmail("unknown@users.noreply.github.com");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for non-noreply email when not found")
        void shouldReturnNullForNonNoreplyEmailWhenNotFound() {
            when(userRepository.findByEmail("personal@gmail.com")).thenReturn(Optional.empty());

            Long result = resolver.resolveByEmail("personal@gmail.com");

            assertThat(result).isNull();
            // Should NOT try findByLogin since it's not a noreply email
            verify(userRepository, never()).findByLogin(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("should prefer direct email match over noreply parsing")
        void shouldPreferDirectEmailMatchOverNoreplyParsing() {
            // Edge case: noreply email is stored directly in user's email field
            User user = createUser(55L);
            when(userRepository.findByEmail("dev@users.noreply.github.com")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByEmail("dev@users.noreply.github.com");

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
            Long result = resolver.resolveByLogin(null);
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return null for blank login")
        void shouldReturnNullForBlankLogin() {
            Long result = resolver.resolveByLogin("  ");
            assertThat(result).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should return user ID when login found")
        void shouldReturnUserIdWhenLoginFound() {
            User user = createUser(33L);
            when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(user));

            Long result = resolver.resolveByLogin("testuser");

            assertThat(result).isEqualTo(33L);
        }

        @Test
        @DisplayName("should return null when login not found")
        void shouldReturnNullWhenLoginNotFound() {
            when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

            Long result = resolver.resolveByLogin("unknown");

            assertThat(result).isNull();
        }
    }
}
