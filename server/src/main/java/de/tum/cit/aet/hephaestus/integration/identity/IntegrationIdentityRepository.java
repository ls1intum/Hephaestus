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

    List<IntegrationIdentity> findByHephaestusUserId(long hephaestusUserId);

    List<IntegrationIdentity> findByExternalEmail(String email);
}
