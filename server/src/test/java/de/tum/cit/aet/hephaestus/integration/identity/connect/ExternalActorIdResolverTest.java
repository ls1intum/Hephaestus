package de.tum.cit.aet.hephaestus.integration.identity.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The login-time external-actor bind PERSISTS its result onto {@code identity_link.external_actor_id},
 * so these tests pin the safety rules: native id wins over login, the login fallback only binds a
 * unique exact match, and nothing here may throw out of the OAuth login flow.
 */
class ExternalActorIdResolverTest extends BaseUnitTest {

    private static final long PROVIDER_ID = 5L;

    @Mock
    private UserRepository userRepository;

    private ExternalActorIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExternalActorIdResolver(userRepository);
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("a numeric subject resolves by native id and never consults the login")
    void nativeIdTakesPrecedence() {
        when(userRepository.findByNativeIdAndProviderId(123L, PROVIDER_ID)).thenReturn(Optional.of(user(42L)));

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "123", "octocat")).contains(42L);

        verify(userRepository, never()).findAllByExactLoginAndProviderId("octocat", PROVIDER_ID);
    }

    @Test
    @DisplayName("falls back to a unique exact login match when no actor carries the native id")
    void loginFallbackBindsUniqueMatch() {
        when(userRepository.findByNativeIdAndProviderId(123L, PROVIDER_ID)).thenReturn(Optional.empty());
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(List.of(user(7L)));

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "123", "octocat")).contains(7L);
    }

    @Test
    @DisplayName("an ambiguous login resolves to no bind and does not throw out of the login flow")
    void ambiguousLoginDoesNotBind() {
        when(userRepository.findByNativeIdAndProviderId(123L, PROVIDER_ID)).thenReturn(Optional.empty());
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(
            List.of(user(7L), user(8L))
        );

        assertThatCode(() -> {
            assertThat(resolver.findExternalActorId(PROVIDER_ID, "123", "octocat")).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a non-numeric subject with a blank or missing username resolves to no bind")
    void blankUsernameWithoutNativeIdResolvesEmpty() {
        assertThat(resolver.findExternalActorId(PROVIDER_ID, "U12345ABC", null)).isEmpty();
        assertThat(resolver.findExternalActorId(PROVIDER_ID, "U12345ABC", "  ")).isEmpty();
        verify(userRepository, never()).findByNativeIdAndProviderId(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    @DisplayName("no synced actor at all resolves to no bind")
    void noMatchResolvesEmpty() {
        when(userRepository.findByNativeIdAndProviderId(123L, PROVIDER_ID)).thenReturn(Optional.empty());
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(List.of());

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "123", "octocat")).isEmpty();
    }
}
