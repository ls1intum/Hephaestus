package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("The primary key is the owning workspace ID")
public interface PracticeCatalogInstallationRepository extends JpaRepository<PracticeCatalogInstallation, Long> {}
