package de.tum.in.www1.hephaestus.gitprovider.team.permission;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepositoryPermissionRepository
    extends JpaRepository<TeamRepositoryPermission, TeamRepositoryPermission.Id> {
    Optional<TeamRepositoryPermission> findByTeam_IdAndRepository_Id(Long teamId, Long repositoryId);
}
