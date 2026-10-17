package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry.ActorProfile;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.MockSecurityContextUtils;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentAccountUsersTest extends BaseUnitTest {
    private final AccountIdentityQuery identities = mock(AccountIdentityQuery.class);
    private final GitProviderRegistry providers = mock(GitProviderRegistry.class);
    private final CurrentAccountUsers resolver = new CurrentAccountUsers(identities, providers);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveTheLiveLoginWhenTheProviderRenamesAnActor() {
        when(identities.activeLinksForAccount(42L)).thenReturn(List.of(link(1L)));
        actor(1L, 7L, "new-login");
        assertThat(resolver.resolve(42L)).extracting(ActorProfile::login).containsExactly("new-login");
    }

    @Test
    void shouldKeepEqualSubjectsOnDifferentProvidersSeparate() {
        when(identities.activeLinksForAccount(42L)).thenReturn(List.of(link(1L), link(2L)));
        actor(1L, 7L, "alice");
        actor(2L, 8L, "alice");
        assertThat(resolver.resolve(42L)).extracting(ActorProfile::actorId).containsExactly(7L, 8L);
    }

    @Test
    void shouldKeepLinkOrderAndDeduplicateRepeatedActors() {
        when(identities.activeLinksForAccount(42L)).thenReturn(List.of(link(1L), link(1L)));
        actor(1L, 7L, "alice");
        assertThat(resolver.resolve(42L)).extracting(ActorProfile::actorId).containsExactly(7L);
    }

    @Test
    void shouldSkipScopedIdentitiesWhenResolvingScmActors() {
        when(identities.activeLinksForAccount(42L))
                .thenReturn(List.of(new AccountIdentityQuery.IdentityView(1L, 1L, "123", "team")));
        assertThat(resolver.resolve(42L)).isEmpty();
        verifyNoInteractions(providers);
    }

    @Test
    void shouldResolveAuthenticatedAccountWithoutUsingTheLoginClaim() {
        SecurityContextHolder.setContext(
                MockSecurityContextUtils.createSecurityContext("unrelated", "42", new String[0], "token"));
        when(identities.activeLinksForAccount(42L)).thenReturn(List.of(link(1L)));
        actor(1L, 7L, "alice");
        assertThat(resolver.resolve()).extracting(ActorProfile::actorId).containsExactly(7L);
    }

    @Test
    void shouldReturnNoActorsWhenNoVerifiedMirrorExists() {
        when(identities.activeLinksForAccount(42L)).thenReturn(List.of(link(1L)));
        assertThat(resolver.resolve(42L)).isEmpty();
    }

    @Test
    void shouldReturnNoActorsWhenNoAccountIsAuthenticated() {
        assertThat(resolver.resolve()).isEmpty();
        verifyNoInteractions(identities, providers);
    }

    private static AccountIdentityQuery.IdentityView link(long providerId) {
        return new AccountIdentityQuery.IdentityView(1L, providerId, "123", "");
    }

    private void actor(long providerId, long actorId, String login) {
        when(providers.findActorId(providerId, "123")).thenReturn(Optional.of(actorId));
        when(providers.actor(actorId))
                .thenReturn(Optional.of(new ActorProfile(
                        actorId, providerId, "GITHUB", "https://github.com", "123", login, login, true)));
    }
}
