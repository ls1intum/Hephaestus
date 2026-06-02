package de.tum.cit.aet.hephaestus.integration.core.connection.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery.IdentityLinkView;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abuse-case hardening for the JWT → SCM-user provisioner. Separate from
 * {@code AuthenticatedGitProviderUserServiceTest} (owned by another wave) to avoid edit
 * collisions; these are additive security regressions, not edits to existing cases.
 *
 * <ul>
 *   <li><b>(a) Non-numeric subject → CONFLICT.</b> {@code IdentityLink.subject} must be the
 *       IdP-stable numeric provider id (the nOAuth defence, enforced via
 *       {@code userNameAttributeName("id")}). A non-numeric subject means a mis-configured
 *       registration mapped a mutable username as the subject; the provisioner must refuse
 *       (HTTP 409) rather than mint an SCM actor keyed on a forgeable handle.</li>
 *   <li><b>(b) {@code linkExternalActorIfAbsent} never clobbers a set actor.</b> The
 *       {@code IdentityLink → ExternalActor} wiring is write-once: once an actor id is bound, a
 *       later login (or a maliciously replayed one) must NOT be able to repoint it at a
 *       different actor — that would be a cross-account hijack of "your activity". This is a
 *       real DB-state assertion against the {@code WHERE external_actor_id IS NULL} guard.</li>
 * </ul>
 */
class AuthenticatedGitProviderUserServiceSecurityTest extends BaseIntegrationTest {

    private static final long ACCOUNT_ID = 42L;
    private static final long GITLAB_PROVIDER_ID = 7L;

    // ── (a) non-numeric subject → CONFLICT (parseSubject path) ────────────────────────────────

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "ES256")
            .subject(String.valueOf(ACCOUNT_ID))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveOrProvision_nonNumericSubject_throwsConflict() {
        // The service collaborators are mocked here — this case never touches the DB; it pins the
        // parseSubject guard in isolation. (Section (b) below uses the real repository.)
        UserRepository userRepository = mock(UserRepository.class);
        GitProviderRepository gitProviderRepository = mock(GitProviderRepository.class);
        AccountIdentityQuery accountIdentityQuery = mock(AccountIdentityQuery.class);
        AuthenticatedGitProviderUserService service = new AuthenticatedGitProviderUserService(
            userRepository,
            gitProviderRepository,
            accountIdentityQuery
        );

        when(userRepository.getCurrentUser()).thenReturn(Optional.empty());
        // subject = "attacker-handle" is NON-numeric → parseSubject must reject it.
        IdentityLinkView poisoned = new IdentityLinkView(
            100L,
            GITLAB_PROVIDER_ID,
            "attacker-handle",
            "attacker-handle",
            "Display",
            null,
            null,
            null
        );
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(poisoned));
        GitProvider provider = new GitProvider(GitProviderType.GITLAB, "https://gitlab.lrz.de");
        provider.setId(GITLAB_PROVIDER_ID);
        when(gitProviderRepository.findById(GITLAB_PROVIDER_ID)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service.resolveOrProvisionCurrentUser())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        // No SCM actor minted and no IdentityLink → actor wiring on a poisoned subject.
        verify(userRepository, never()).upsertUser(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(accountIdentityQuery, never()).linkExternalActor(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        );
    }

    // ── (b) linkExternalActorIfAbsent must not clobber a set externalActorId (hijack guard) ────

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @Transactional // @Modifying query requires an active tx; JdbcTemplate shares the connection so it
    // observes the uncommitted state. The tx rolls back at test end (cleanup).
    void linkExternalActorIfAbsent_doesNotClobberAlreadySetActor() {
        // An IdentityLink already bound to actor 1000 (the legitimate "your activity" owner).
        Account account = accountRepository.save(new Account("Victim"));
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProviderId(GITLAB_PROVIDER_ID);
        link.setSubject("18024");
        link.setUsernameAtSignup("victim");
        link.setExternalActorId(1000L);
        link = identityLinkRepository.saveAndFlush(link); // flush so the JPQL UPDATE sees the row
        Long linkId = link.getId();

        // A second resolution tries to repoint the link at a DIFFERENT actor (2000).
        int updated = identityLinkRepository.linkExternalActorIfAbsent(linkId, 2000L);

        // The WHERE external_actor_id IS NULL guard must reject the write entirely.
        assertThat(updated).as("clobber attempt must update zero rows").isZero();

        // Observable DB state: the actor binding is unchanged (read straight from the row, not the
        // persistence context, so a stale first-level cache cannot mask a real overwrite).
        Long persistedActorId = jdbcTemplate.queryForObject(
            "SELECT external_actor_id FROM identity_link WHERE id = ?",
            Long.class,
            linkId
        );
        assertThat(persistedActorId).isEqualTo(1000L);
    }

    @Test
    @Transactional
    void linkExternalActorIfAbsent_wiresWhenAbsent() {
        // Control: when the actor is unset the idempotent wiring DOES bind it (first login path).
        Account account = accountRepository.save(new Account("Newcomer"));
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProviderId(GITLAB_PROVIDER_ID);
        link.setSubject("18025");
        link.setUsernameAtSignup("newcomer");
        link = identityLinkRepository.saveAndFlush(link); // flush so the JPQL UPDATE sees the row
        Long linkId = link.getId();

        int updated = identityLinkRepository.linkExternalActorIfAbsent(linkId, 3000L);

        assertThat(updated).isEqualTo(1);
        Long persistedActorId = jdbcTemplate.queryForObject(
            "SELECT external_actor_id FROM identity_link WHERE id = ?",
            Long.class,
            linkId
        );
        assertThat(persistedActorId).isEqualTo(3000L);
    }
}
