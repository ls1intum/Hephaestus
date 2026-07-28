package de.tum.cit.aet.hephaestus.integration.identity.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * The bind is persisted and never re-evaluated, so a wrong answer permanently attaches one person's account
 * to another person's work. These tests are about the resolver preferring silence.
 */
class ExternalActorIdResolverTest extends BaseUnitTest {

    private static final long PROVIDER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExternalActorIdResolver resolver;

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("the provider-native id wins over the login")
    void nativeIdIsPreferredOverLogin() {
        when(userRepository.findByNativeIdAndProviderId(4242L, PROVIDER_ID)).thenReturn(Optional.of(user(9L)));
        // The login resolves to a DIFFERENT actor, so an inverted lookup order fails this.
        lenient()
            .when(userRepository.findAllByExactLoginAndProviderId("renamed-since", PROVIDER_ID))
            .thenReturn(List.of(user(77L)));

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "4242", "renamed-since")).contains(9L);
    }

    @Test
    @DisplayName("a non-numeric subject falls back to the login")
    void nonNumericSubjectFallsBackToLogin() {
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(List.of(user(9L)));

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "not-a-number", "octocat")).contains(9L);
    }

    @Test
    @DisplayName("an unsynced actor resolves to empty")
    void unknownIdentityResolvesToEmpty() {
        lenient().when(userRepository.findByNativeIdAndProviderId(4242L, PROVIDER_ID)).thenReturn(Optional.empty());
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(List.of());

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "4242", "octocat")).isEmpty();
    }

    @Test
    @DisplayName("an ambiguous login binds nothing")
    void ambiguousLoginBindsNothing() {
        lenient().when(userRepository.findByNativeIdAndProviderId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(userRepository.findAllByExactLoginAndProviderId("octocat", PROVIDER_ID)).thenReturn(
            List.of(user(9L), user(10L))
        );

        assertThat(resolver.findExternalActorId(PROVIDER_ID, "not-a-number", "octocat")).isEmpty();
    }

    @Test
    @DisplayName("a blank or null login resolves to empty")
    void blankLoginShortCircuits() {
        assertThat(resolver.findExternalActorId(PROVIDER_ID, "not-a-number", "  ")).isEmpty();
        assertThat(resolver.findExternalActorId(PROVIDER_ID, "not-a-number", null)).isEmpty();
    }

    @Test
    @DisplayName("recipient logins resolve; an unknown id is absent")
    void resolvesRecipientLoginsAndToleratesUnknownIds() {
        User octocat = user(9L);
        octocat.setLogin("octocat");
        when(userRepository.findAllById(List.of(9L, 404L))).thenReturn(List.of(octocat));

        assertThat(resolver.loginsByActorIds(List.of(9L, 404L))).containsExactly(entry(9L, "octocat"));
    }
}
