package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-{@code workspace}-module implementation of {@link AccountWorkspaceMembershipQuery}. Lives
 * here so it can touch {@link WorkspaceMembership} / {@link Workspace} directly; exposes only the
 * narrow auth-spi contract to {@code core.auth} (dependency inversion — the interface is owned by
 * {@code core.auth}, the implementation by the data owner). Mirrors the
 * {@code AccountRoleQueryService} pattern.
 */
@Service
public class AccountWorkspaceMembershipQueryAdapter implements AccountWorkspaceMembershipQuery {

    private static final Logger log = LoggerFactory.getLogger(AccountWorkspaceMembershipQueryAdapter.class);

    private final WorkspaceMembershipRepository workspaceMembershipRepository;

    public AccountWorkspaceMembershipQueryAdapter(WorkspaceMembershipRepository workspaceMembershipRepository) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMembershipView> membershipsForLogins(Set<String> logins) {
        if (logins == null || logins.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = logins
            .stream()
            .filter(l -> l != null && !l.isBlank())
            .map(l -> l.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return List.of();
        }
        try {
            // Deduplicate by workspace: a principal may resolve to multiple logins that both map
            // to the same workspace membership graph; the export should list each workspace once.
            Map<Long, WorkspaceMembershipView> byWorkspace = new LinkedHashMap<>();
            for (WorkspaceMembership membership : workspaceMembershipRepository.findAllWithWorkspaceByUserLoginInLowercase(
                normalized
            )) {
                Workspace workspace = membership.getWorkspace();
                if (workspace == null || workspace.getId() == null) {
                    continue;
                }
                byWorkspace.putIfAbsent(
                    workspace.getId(),
                    new WorkspaceMembershipView(
                        workspace.getId(),
                        workspace.getWorkspaceSlug(),
                        workspace.getDisplayName(),
                        membership.getRole() != null ? membership.getRole().name() : null,
                        membership.getUser() != null ? membership.getUser().getId() : null
                    )
                );
            }
            return List.copyOf(byWorkspace.values());
        } catch (RuntimeException e) {
            // Fail-soft: a partial export is preferable to a failed one for a self-service GDPR
            // request. The empty section is visible in the bundle, and the error is logged.
            log.error("auth.export: workspace-membership lookup failed for {} login(s)", normalized.size(), e);
            return List.of();
        }
    }
}
