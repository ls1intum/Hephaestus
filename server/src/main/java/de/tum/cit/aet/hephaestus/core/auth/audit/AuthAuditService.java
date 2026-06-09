package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the {@code auth_event} audit log for the instance-admin viewer. Thin query service so
 * the controller delegates rather than touching the repository directly. Auth events are
 * account/system-scoped (not workspace-scoped), hence {@link WorkspaceAgnostic}.
 *
 * <p>Resolves the subject + actor account ids on a page to human identities in a single batch lookup,
 * so the viewer shows names/emails rather than bare primary keys. Audit rows outlive accounts (deletion,
 * GDPR redaction), so a missing account resolves to {@code null} and the UI falls back to the id.
 */
@Service
@WorkspaceAgnostic("Auth audit events are account/system-scoped, not workspace-scoped")
public class AuthAuditService {

    private final AuthEventRepository authEventRepository;
    private final AccountRepository accountRepository;

    public AuthAuditService(AuthEventRepository authEventRepository, AccountRepository accountRepository) {
        this.authEventRepository = authEventRepository;
        this.accountRepository = accountRepository;
    }

    /** Audit-viewer filters; every field null = unfiltered. */
    public record Filter(
        @Nullable Long accountId,
        @Nullable Long actingAccountId,
        AuthEvent.@Nullable EventType eventType,
        AuthEvent.@Nullable Result result,
        @Nullable Instant from,
        @Nullable Instant to
    ) {}

    /** A human-readable account identity for an audit row. {@code displayName}/{@code email} may be null. */
    public record AccountRef(long id, @Nullable String displayName, @Nullable String email) {}

    /** A page of audit events plus the resolved identities for the accounts referenced on that page. */
    public record AuditPage(Page<AuthEvent> events, Map<Long, AccountRef> identities) {}

    /** Auth events newest-first for the given filter, with the page's account identities resolved. */
    @Transactional(readOnly = true)
    public AuditPage list(Filter filter, Pageable pageable) {
        Page<AuthEvent> events = authEventRepository.findForAdmin(
            filter.accountId(),
            filter.actingAccountId(),
            filter.eventType(),
            filter.result(),
            filter.from(),
            filter.to(),
            pageable
        );
        Set<Long> ids = new HashSet<>();
        for (AuthEvent e : events.getContent()) {
            if (e.getAccountId() != null) {
                ids.add(e.getAccountId());
            }
            if (e.getActingAccountId() != null) {
                ids.add(e.getActingAccountId());
            }
        }
        Map<Long, AccountRef> identities = ids.isEmpty()
            ? Map.of()
            : accountRepository
                  .findAllById(ids)
                  .stream()
                  .collect(
                      Collectors.toMap(
                          Account::getId,
                          a -> new AccountRef(a.getId(), a.getDisplayName(), a.getPrimaryEmail()),
                          (a, b) -> a
                      )
                  );
        return new AuditPage(events, identities);
    }

    /** Resolve an id (subject or actor) to its identity; null id or unknown account → null. */
    public static @Nullable AccountRef refOf(@Nullable Long id, Map<Long, AccountRef> identities) {
        return id == null ? null : identities.get(id);
    }
}
