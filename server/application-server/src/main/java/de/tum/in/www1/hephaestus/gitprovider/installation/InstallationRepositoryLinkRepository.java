package de.tum.in.www1.hephaestus.gitprovider.installation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationRepositoryLinkRepository
    extends JpaRepository<InstallationRepositoryLink, InstallationRepositoryLink.Id> {
    Optional<InstallationRepositoryLink> findByIdInstallationIdAndIdRepositoryId(
        Long installationId,
        Long repositoryId
    );
    List<InstallationRepositoryLink> findAllByIdInstallationId(Long installationId);
}
