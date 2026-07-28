package de.tum.cit.aet.hephaestus.core.auth.impersonation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventData;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipal;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.jwt.TokenConstraints;
import de.tum.cit.aet.hephaestus.core.auth.stepup.StepUpPolicy;
import de.tum.cit.aet.hephaestus.core.auth.stepup.StepUpRequiredException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Behavioral suite for {@link ImpersonationService}: the begin() authorization gates
 * (operator-must-be-admin, no self-impersonation, target-must-exist, no admin→admin), the RFC 8693
 * {@code act}-claim + audit-pair contract, exit() revocation + operator re-mint, and the
 * audit-integrity regression (operator-supplied {@code reason} JSON-escaped via Jackson so control
 * characters never corrupt {@code auth_event.details}).
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

    @Mock
    private StepUpPolicy stepUpPolicy;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ImpersonationService service;

    /** The mocked-out (freely passing) StepUpPolicy is exercised separately in the step-up tests. */
    @BeforeEach
    void setUp() {
        AuthEventLogger logger = new AuthEventLogger(authEventWriter);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuthProperties properties = mock(AuthProperties.class);
        Mockito.lenient().when(properties.impersonationMaxLifetime()).thenReturn(java.time.Duration.ofHours(1));
        service = new ImpersonationService(
            accountRepository,
            jwtIssuer,
            principalFactory,
            issuedJwtRepository,
            logger,
            objectMapper,
            properties,
            stepUpPolicy,
            clock
        );
    }

    private static final ImpersonationService.OperatorSession NO_SESSION = new ImpersonationService.OperatorSession(
        null,
        null
    );

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
        when(principalFactory.forAccount(target)).thenReturn(new JwtPrincipal(2L, "target", "Target", Set.of()));
        when(jwtIssuer.issue(any(), any(TokenConstraints.class), any())).thenReturn(
            new HephaestusJwtIssuer.Token("tok", UUID.randomUUID(), Instant.parse("2026-01-01T00:15:00Z"))
        );

        // A reason laden with characters that the old escape() missed: newline, tab, quote, backslash.
        String nastyReason = "line1\nline2\twith \"quotes\" and a \\ backslash";
        service.begin(1L, 2L, nastyReason, NO_SESSION, null);

        ArgumentCaptor<AuthEventData> captor = ArgumentCaptor.forClass(AuthEventData.class);
        verify(authEventWriter).write(captor.capture());
        String details = captor.getValue().details();

        // Must be parseable JSON and round-trip the exact reason (control chars preserved).
        JsonNode parsed = objectMapper.readTree(details);
        assertThat(parsed.get("reason").asString()).isEqualTo(nastyReason);
    }

    @Test
    void begin_whenOperatorNotAdmin_forbidden() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, Account.AppRole.USER)));

        assertThatThrownBy(() -> service.begin(1L, 2L, "support", NO_SESSION, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(jwtIssuer, never()).issue(any(), any(), any());
        verify(authEventWriter, never()).write(any());
    }

    @Test
    void begin_whenSelfImpersonation_badRequest() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, Account.AppRole.APP_ADMIN)));

        assertThatThrownBy(() -> service.begin(1L, 1L, "support", NO_SESSION, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );

        verify(jwtIssuer, never()).issue(any(), any(), any());
        verify(authEventWriter, never()).write(any());
    }

    @Test
    void begin_whenTargetNotFound_notFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, Account.AppRole.APP_ADMIN)));
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.begin(1L, 2L, "support", NO_SESSION, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(jwtIssuer, never()).issue(any(), any(), any());
        verify(authEventWriter, never()).write(any());
    }

    @Test
    void begin_recordsActClaimTokenAndAuditPair() {
        Account operator = account(1L, Account.AppRole.APP_ADMIN);
        Account target = account(2L, Account.AppRole.USER);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(principalFactory.forAccount(target)).thenReturn(new JwtPrincipal(2L, "target", "Target", Set.of()));
        // act-claim contract: the issuer is invoked with the OPERATOR id as the impersonatorId, the
        // time-box imp_exp = now + impersonationMaxLifetime (1h from the fixed clock), and the
        // operator's own session ceiling + auth_time carried into the impersonation sub-session.
        Instant sessionCeiling = Instant.parse("2026-01-01T08:00:00Z");
        Instant authTime = Instant.parse("2025-12-31T23:58:00Z");
        TokenConstraints expected = TokenConstraints.impersonation(
            1L,
            Instant.parse("2026-01-01T01:00:00Z"),
            sessionCeiling,
            authTime
        );
        when(jwtIssuer.issue(any(), eq(expected), any())).thenReturn(
            new HephaestusJwtIssuer.Token("tok", UUID.randomUUID(), Instant.parse("2026-01-01T00:15:00Z"))
        );

        ImpersonationService.Result result = service.begin(
            1L,
            2L,
            "support",
            new ImpersonationService.OperatorSession(sessionCeiling, authTime),
            null
        );

        assertThat(result.targetAccountId()).isEqualTo(2L);
        assertThat(result.actingAccountId()).isEqualTo(1L);
        verify(jwtIssuer).issue(any(), eq(expected), any());

        ArgumentCaptor<AuthEventData> captor = ArgumentCaptor.forClass(AuthEventData.class);
        verify(authEventWriter).write(captor.capture());
        AuthEventData event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuthEvent.EventType.IMPERSONATION_BEGIN);
        assertThat(event.result()).isEqualTo(AuthEvent.Result.SUCCESS);
        assertThat(event.accountId()).isEqualTo(2L);
        assertThat(event.actingAccountId()).isEqualTo(1L);
    }

    @Test
    void exit_revokesImpersonationJtiAndMintsOperatorToken() {
        UUID impersonationJti = UUID.randomUUID();
        when(principalFactory.forAccountId(1L)).thenReturn(
            new JwtPrincipal(1L, "operator", "Operator", Set.of("admin"))
        );
        Instant sessionCeiling = Instant.parse("2026-01-01T08:00:00Z");
        Instant authTime = Instant.parse("2025-12-31T23:58:00Z");
        when(jwtIssuer.issue(any(), eq(TokenConstraints.session(sessionCeiling, authTime)), any())).thenReturn(
            new HephaestusJwtIssuer.Token("op-tok", UUID.randomUUID(), Instant.parse("2026-01-01T00:15:00Z"))
        );

        ImpersonationService.Result result = service.exit(
            1L,
            2L,
            impersonationJti,
            new ImpersonationService.OperatorSession(sessionCeiling, authTime),
            null
        );

        verify(issuedJwtRepository).revoke(eq(impersonationJti), any(), eq(IssuedJwt.RevokedReason.IMPERSONATION_EXIT));
        // The operator session is restored under its ORIGINAL ceiling + auth_time — no fresh
        // unlimited session on exit.
        verify(jwtIssuer).issue(any(), eq(TokenConstraints.session(sessionCeiling, authTime)), any());
        assertThat(result.targetAccountId()).isEqualTo(1L);
        assertThat(result.actingAccountId()).isNull();

        ArgumentCaptor<AuthEventData> captor = ArgumentCaptor.forClass(AuthEventData.class);
        verify(authEventWriter).write(captor.capture());
        AuthEventData event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuthEvent.EventType.IMPERSONATION_END);
        assertThat(event.accountId()).isEqualTo(2L);
        assertThat(event.actingAccountId()).isEqualTo(1L);
    }

    @Test
    void begin_whenTargetIsAppAdmin_isForbiddenBeforeTheStepUpGateRuns() {
        // Anti-escalation guard (no admin→admin) AND the gate's ordering contract: the gate runs LAST,
        // so an ordinary validation error surfaces as itself and an audited step-up denial only ever
        // reflects a request that would otherwise have succeeded. Move the gate to the top of begin()
        // and this fails with StepUpRequiredException instead.
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, Account.AppRole.APP_ADMIN)));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account(2L, Account.AppRole.APP_ADMIN)));
        // Armed to deny — lenient because reaching it at all is the failure this test looks for.
        Mockito.lenient()
            .doThrow(new StepUpRequiredException(java.time.Duration.ofMinutes(5)))
            .when(stepUpPolicy)
            .requireRecentAuthentication(any(), any(), any(), any());

        assertThatThrownBy(() -> service.begin(1L, 2L, "support", NO_SESSION, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(jwtIssuer, never()).issue(any(), any(), any());
        verify(authEventWriter, never()).write(any());
    }
}
