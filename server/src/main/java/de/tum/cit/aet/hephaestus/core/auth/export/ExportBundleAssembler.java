package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.AccountService;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the {@link ExportBundle} for one account by aggregating data the principal owns from
 * five sources:
 *
 * <ol>
 *   <li><b>account profile</b> + <b>own identity links</b> — {@link AccountService} ({@code core.auth} domain)</li>
 *   <li><b>feature flags</b> — {@link AccountFeatureRepository} ({@code core.auth} domain)</li>
 *   <li><b>auth events (last 12 months)</b> — {@link AuthEventRepository} ({@code core.auth} audit)</li>
 *   <li><b>workspace memberships</b> — {@link AccountWorkspaceMembershipQuery} (auth-spi → {@code workspace})</li>
 *   <li><b>account preferences</b> — {@link AccountPreferencesQuery} (auth-spi → {@code account})</li>
 * </ol>
 *
 * <p>The two cross-module sources are reached only through the {@code core.auth.spi} named
 * interface (implemented in {@code workspace} / {@code account}); this module never imports those
 * modules' domain types. The {@code Account → login} bridge ({@code IdentityLink.usernameAtSignup})
 * is owned here and fed to the workspace/preferences queries.
 *
 * <h2>Disclosure discipline</h2>
 * Only the fields enumerated in {@link ExportBundle} are emitted. Tokens, encrypted credential
 * blobs, JWT signing keys, password-equivalents (there are none in this system), and any other
 * account's data are structurally excluded — there is no code path here that reads them.
 */
@Component
@WorkspaceAgnostic("GDPR export aggregates an account's own data across workspaces; not workspace-scoped")
public class ExportBundleAssembler {

    /** GDPR Art. 20 auth-event window. */
    private static final int AUTH_EVENT_WINDOW_MONTHS = 12;

    private final AccountService accountService;
    private final AccountFeatureRepository accountFeatureRepository;
    private final AuthEventRepository authEventRepository;
    private final AccountWorkspaceMembershipQuery workspaceMembershipQuery;
    private final AccountPreferencesQuery preferencesQuery;
    private final Clock clock;

    public ExportBundleAssembler(
        AccountService accountService,
        AccountFeatureRepository accountFeatureRepository,
        AuthEventRepository authEventRepository,
        AccountWorkspaceMembershipQuery workspaceMembershipQuery,
        AccountPreferencesQuery preferencesQuery,
        Clock clock
    ) {
        this.accountService = accountService;
        this.accountFeatureRepository = accountFeatureRepository;
        this.authEventRepository = authEventRepository;
        this.workspaceMembershipQuery = workspaceMembershipQuery;
        this.preferencesQuery = preferencesQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExportBundle assemble(Long accountId) {
        Account account = accountService.requireById(accountId);
        List<IdentityLink> identities = accountService.activeIdentities(accountId);

        Set<String> logins = new LinkedHashSet<>();
        for (IdentityLink link : identities) {
            if (link.getUsernameAtSignup() != null && !link.getUsernameAtSignup().isBlank()) {
                logins.add(link.getUsernameAtSignup());
            }
        }

        ExportBundle.Profile profile = new ExportBundle.Profile(
            account.getId(),
            account.getDisplayName(),
            account.getPrimaryEmail(),
            account.getAppRole().name(),
            account.getStatus().name(),
            account.getCreatedAt()
        );

        List<ExportBundle.Identity> identityViews = identities
            .stream()
            .map(ExportBundleAssembler::toIdentity)
            .toList();

        List<ExportBundle.WorkspaceMembership> memberships = workspaceMembershipQuery
            .membershipsForLogins(logins)
            .stream()
            .map(m -> new ExportBundle.WorkspaceMembership(m.workspaceSlug(), m.workspaceName(), m.role()))
            .toList();

        List<String> featureFlags = accountFeatureRepository.findFlagsByAccountId(accountId);

        // Preferences are keyed by a single SCM login; use the principal's primary (first active)
        // login. Absent if no preferences row exists yet.
        ExportBundle.Preferences preferences = logins
            .stream()
            .findFirst()
            .flatMap(preferencesQuery::preferencesForLogin)
            .map(p -> new ExportBundle.Preferences(p.participateInResearch(), p.aiReviewEnabled()))
            .orElse(null);

        Instant since = Instant.now(clock).minus(AUTH_EVENT_WINDOW_MONTHS * 30L, ChronoUnit.DAYS);
        List<ExportBundle.AuthEvent> authEvents = authEventRepository
            .findByAccountSince(accountId, since)
            .stream()
            .map(ExportBundleAssembler::toAuthEvent)
            .toList();

        return new ExportBundle(
            ExportBundle.SCHEMA_VERSION,
            Instant.now(clock),
            profile,
            identityViews,
            memberships,
            featureFlags,
            preferences,
            authEvents
        );
    }

    private static ExportBundle.Identity toIdentity(IdentityLink il) {
        String provider = il.getGitProvider() != null ? il.getGitProvider().getType().name() : "OIDC";
        return new ExportBundle.Identity(
            provider,
            il.getSubject(),
            il.getUsernameAtSignup(),
            il.getEmailAtSignup(),
            il.getDisplayName(),
            il.getLinkedAt(),
            il.getLastLoginAt()
        );
    }

    private static ExportBundle.AuthEvent toAuthEvent(
        de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent e
    ) {
        return new ExportBundle.AuthEvent(
            e.getId() != null ? e.getId().getOccurredAt() : null,
            e.getEventType() != null ? e.getEventType().name() : null,
            e.getResult() != null ? e.getResult().name() : null,
            e.getIpInet(),
            e.getUserAgent()
        );
    }
}
