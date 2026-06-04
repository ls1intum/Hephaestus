package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the {@code auth_event} audit log for the instance-admin viewer. Thin query service so
 * the controller delegates rather than touching the repository directly. Auth events are
 * account/system-scoped (not workspace-scoped), hence {@link WorkspaceAgnostic}.
 */
@Service
@WorkspaceAgnostic("Auth audit events are account/system-scoped, not workspace-scoped")
public class AuthAuditService {

    private final AuthEventRepository authEventRepository;

    public AuthAuditService(AuthEventRepository authEventRepository) {
        this.authEventRepository = authEventRepository;
    }

    /** Auth events newest-first, optionally narrowed by subject account and/or event type. */
    @Transactional(readOnly = true)
    public Page<AuthEvent> list(@Nullable Long accountId, AuthEvent.EventType eventType, Pageable pageable) {
        return authEventRepository.findForAdmin(accountId, eventType, pageable);
    }
}
