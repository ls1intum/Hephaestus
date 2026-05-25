package de.tum.cit.aet.hephaestus.integration.identity;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("integration_identity is scoped by (kind, integration_instance_id) — for SCM this is the shared git_provider id (cross-workspace identity); for Slack/Outline this is the connection id. Tenant isolation is enforced by the integration_instance_id semantics, not workspace_id.")
public interface IntegrationIdentityRepository extends JpaRepository<IntegrationIdentity, Long> {

    Optional<IntegrationIdentity> findByKindAndIntegrationInstanceIdAndExternalId(
        IntegrationKind kind, long integrationInstanceId, String externalId);

    /**
     * Cross-instance lookup by (kind, external_id). Used by the GitHub App bind
     * identity check: we need to know whether the installer's GitHub user id is
     * linked to any Hephaestus user. For SCM kinds the {@code integration_instance_id}
     * column resolves to the shared {@code git_provider} id, so in practice this
     * returns 0 or 1 rows per (kind, external_id) — the explicit list shape keeps
     * the method honest for messaging kinds where multiple connections exist.
     */
    List<IntegrationIdentity> findByKindAndExternalId(IntegrationKind kind, String externalId);

    List<IntegrationIdentity> findByHephaestusUserId(long hephaestusUserId);

    List<IntegrationIdentity> findByExternalEmail(String email);
}
