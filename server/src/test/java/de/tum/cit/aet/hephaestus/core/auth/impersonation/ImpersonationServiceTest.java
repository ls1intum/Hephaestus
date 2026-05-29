package de.tum.cit.aet.hephaestus.core.auth.impersonation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventData;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipal;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Audit-integrity regression for {@link ImpersonationService}. The {@code reason} is operator-
 * supplied free text; it must be serialized via Jackson so control characters never corrupt the
 * {@code auth_event.details} JSON (the old hand-rolled escape() only handled {@code \\} and quotes).
 */
class ImpersonationServiceTest extends BaseUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HephaestusJwtIssuer jwtIssuer;

    @Mock
    private JwtPrincipalFactory principalFactory;

    @Mock
    private IssuedJwtRepository issuedJwtRepository;

    @Mock
    private AuthEventWriter authEventWriter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ImpersonationService service;

    @BeforeEach
    void setUp() {
        AuthEventLogger logger = new AuthEventLogger(authEventWriter);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new ImpersonationService(
            accountRepository,
            jwtIssuer,
            principalFactory,
            issuedJwtRepository,
            logger,
            objectMapper,
            clock
        );
    }

    private static Account account(long id, Account.AppRole role) {
        Account a = new Account();
        a.setId(id);
        a.setAppRole(role);
        a.setDisplayName("acct-" + id);
        return a;
    }

    @Test
    void reasonWithControlCharactersIsSerializedAsValidJson() {
        Account operator = account(1L, Account.AppRole.APP_ADMIN);
        Account target = account(2L, Account.AppRole.USER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(principalFactory.forAccount(target)).thenReturn(
            new JwtPrincipal(2L, "target", "Target", java.util.Set.of())
        );
        when(jwtIssuer.issue(any(), eq(1L), any())).thenReturn(
            new HephaestusJwtIssuer.Token("tok", UUID.randomUUID(), Instant.parse("2026-01-01T00:15:00Z"))
        );

        // A reason laden with characters that the old escape() missed: newline, tab, quote, backslash.
        String nastyReason = "line1\nline2\twith \"quotes\" and a \\ backslash";
        service.begin(1L, 2L, nastyReason, null);

        ArgumentCaptor<AuthEventData> captor = ArgumentCaptor.forClass(AuthEventData.class);
        verify(authEventWriter).write(captor.capture());
        String details = captor.getValue().details();

        // Must be parseable JSON and round-trip the exact reason (control chars preserved).
        JsonNode parsed = objectMapper.readTree(details);
        assertThat(parsed.get("reason").asText()).isEqualTo(nastyReason);
    }
}
