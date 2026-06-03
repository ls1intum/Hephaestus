package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit coverage of {@link AccountService#unlinkIdentity} guard logic with all repository/audit
 * collaborators mocked — including the post-lock race branch ({@code disableByIdAndAccountId == 0}),
 * which the integration test cannot reach. Uses a real {@link AuthEventLogger} over a mock
 * {@link AuthEventWriter} so "did we audit?" is a single {@code write(...)} verification.
 */
class AccountServiceTest extends BaseUnitTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final IdentityLinkRepository identityLinkRepository = mock(IdentityLinkRepository.class);
    private final IssuedJwtRepository issuedJwtRepository = mock(IssuedJwtRepository.class);
    private final AuthEventWriter auditWriter = mock(AuthEventWriter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(
            accountRepository,
            identityLinkRepository,
            issuedJwtRepository,
            new AuthEventLogger(auditWriter),
            clock
        );
    }

    private static IdentityLink link(long id, long gitProviderId) {
        IdentityLink il = mock(IdentityLink.class);
        lenient().when(il.getId()).thenReturn(id);
        lenient().when(il.getGitProviderId()).thenReturn(gitProviderId);
        return il;
    }

    @Test
    void unlinkSecondaryIdentityDisablesItAndAudits() {
        List<IdentityLink> active = List.of(link(10L, 100L), link(11L, 101L));
        when(identityLinkRepository.findActiveByAccountIdForUpdate(1L)).thenReturn(active);
        when(identityLinkRepository.deleteByIdAndAccountId(10L, 1L)).thenReturn(1);

        service.unlinkIdentity(1L, 10L);

        verify(identityLinkRepository).deleteByIdAndAccountId(10L, 1L);
        verify(auditWriter).write(any());
    }

    @Test
    void cannotUnlinkTheOnlyRemainingIdentity() {
        List<IdentityLink> active = List.of(link(10L, 100L));
        when(identityLinkRepository.findActiveByAccountIdForUpdate(1L)).thenReturn(active);

        assertThatThrownBy(() -> service.unlinkIdentity(1L, 10L)).isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );

        verify(identityLinkRepository, never()).deleteByIdAndAccountId(anyLong(), anyLong());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void unlinkingAnIdentityTheAccountDoesNotOwnIs404() {
        List<IdentityLink> active = List.of(link(10L, 100L), link(11L, 101L));
        when(identityLinkRepository.findActiveByAccountIdForUpdate(1L)).thenReturn(active);

        assertThatThrownBy(() -> service.unlinkIdentity(1L, 999L)).isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
        );

        verify(identityLinkRepository, never()).deleteByIdAndAccountId(anyLong(), anyLong());
        verifyNoInteractions(auditWriter);
    }

    @Test
    void lostRaceAfterTheGuardSurfacesAs404() {
        List<IdentityLink> active = List.of(link(10L, 100L), link(11L, 101L));
        when(identityLinkRepository.findActiveByAccountIdForUpdate(1L)).thenReturn(active);
        when(identityLinkRepository.deleteByIdAndAccountId(10L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.unlinkIdentity(1L, 10L)).isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
        );

        verifyNoInteractions(auditWriter);
    }
}
