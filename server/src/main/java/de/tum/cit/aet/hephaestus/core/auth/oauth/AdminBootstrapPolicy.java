package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether a federated login should be promoted to the instance super-admin role
 * ({@code APP_ADMIN}) based on the {@code hephaestus.auth.bootstrap-admins} allowlist.
 *
 * <p>The allowlist is the operator-scoped source of truth for "who runs this instance" — it is
 * deliberately NOT the per-workspace role model (those derive from SCM team / WorkspaceMembership).
 * Entries are {@code <registrationId>:<subject>} where {@code subject} is the IdP-stable numeric
 * provider user id — the exact tuple {@link de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink}
 * is keyed on. Matching on the subject (never email, never username) inherits the same nOAuth /
 * username-reclaim safety the provisioning lookup already relies on.
 *
 * <p>Pure decision only — it never touches the DB. The caller
 * ({@link AccountProvisioningService}) owns the (idempotent, promote-only) mutation inside the login
 * transaction, so the role lands before the JWT is minted.
 */
@Component
@WorkspaceAgnostic("Instance-admin bootstrap is keyed by (provider, subject); not workspace-scoped")
public class AdminBootstrapPolicy {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapPolicy.class);

    /** Normalized {@code registrationId:subject} entries. */
    private final Set<String> allowlist;

    public AdminBootstrapPolicy(AuthProperties properties) {
        this.allowlist = parse(properties.bootstrapAdmins());
        if (!allowlist.isEmpty()) {
            log.info("auth.bootstrap: {} instance-admin allowlist entr(ies) configured", allowlist.size());
        }
    }

    /**
     * @return {@code true} iff {@code (registrationId, subject)} is on the allowlist. Empty/unset
     *     allowlist or null inputs return {@code false} (fail-closed — no admin path exists).
     */
    public boolean shouldPromote(String registrationId, String subject) {
        if (allowlist.isEmpty() || registrationId == null || subject == null) {
            return false;
        }
        return allowlist.contains(registrationId + ":" + subject);
    }

    private static Set<String> parse(List<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            int sep = trimmed.indexOf(':');
            // Require a non-blank provider AND a non-blank subject; silently drop malformed entries
            // (blank, no colon, "provider:" or ":subject") rather than fail boot — but log it.
            if (sep <= 0 || sep == trimmed.length() - 1) {
                if (!trimmed.isEmpty()) {
                    log.warn("auth.bootstrap: ignoring malformed bootstrap-admins entry (expected 'provider:subject')");
                }
                continue;
            }
            out.add(trimmed);
        }
        return Set.copyOf(out);
    }
}
