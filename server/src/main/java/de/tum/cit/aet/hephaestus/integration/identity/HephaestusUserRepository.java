package de.tum.cit.aet.hephaestus.integration.identity;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("HephaestusUser is the Layer-2 identity aggregating one real person across workspaces — not tenant-scoped by design.")
public interface HephaestusUserRepository extends JpaRepository<HephaestusUser, Long> {

    Optional<HephaestusUser> findByKeycloakSubject(String keycloakSubject);

    Optional<HephaestusUser> findByEmail(String email);
}
