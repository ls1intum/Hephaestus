package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes exact identity ownership before account rows are locked. */
@Component
@WorkspaceAgnostic("Exact provider identity ownership spans workspaces")
@RequiredArgsConstructor
class IdentityKeyLock {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(List<PersonIdentity> identities) {
        var keys = identities.stream()
                .map(identity -> {
                    if (identity.providerId() <= 0 || identity.subject().isBlank())
                        throw new IllegalArgumentException("An exact provider identity is required");
                    return Objects.requireNonNull(jdbc.queryForObject(
                            "SELECT" + " hashtextextended(jsonb_build_array(?::bigint,?::text,?::text)::text,0)",
                            Long.class,
                            identity.providerId(),
                            identity.subject(),
                            Objects.requireNonNullElse(identity.teamId(), "")));
                })
                .distinct()
                .sorted()
                .toList();
        for (long key : keys) jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, key);
    }
}
