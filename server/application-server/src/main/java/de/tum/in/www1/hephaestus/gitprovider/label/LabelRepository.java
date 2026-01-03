package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
public interface LabelRepository extends JpaRepository<Label, Long> {
    @Query(
        """
        SELECT l
        FROM Label l
        WHERE l.repository.id = :repositoryId
        AND l.name = :name
        """
    )
    Optional<Label> findByRepositoryIdAndName(@Param("repositoryId") Long repositoryId, @Param("name") String name);

    List<Label> findAllByRepository_Id(Long repositoryId);
}
