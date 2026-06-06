package de.tum.cit.aet.hephaestus.integration.core.connection.identity;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-DB hijack guard for {@code IdentityLink.linkExternalActorIfAbsent}: the
 * {@code IdentityLink → ExternalActor} wiring is write-once. Once an actor id is bound, a later
 * (or maliciously replayed) login must NOT be able to repoint it at a different actor — that would
 * be a cross-account hijack of "your activity". Asserts the {@code WHERE external_actor_id IS NULL}
 * guard against committed DB state. (The pure-logic {@code parseSubject} guard lives in the unit
 * {@code AuthenticatedGitProviderUserServiceTest}.)
 */
class AuthenticatedGitProviderUserServiceSecurityIntegrationTest extends BaseIntegrationTest {

    private static final long GITLAB_PROVIDER_ID = 7L;

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
