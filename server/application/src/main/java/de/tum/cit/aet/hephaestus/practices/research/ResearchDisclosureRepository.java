package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchDisclosureRepository extends JpaRepository<ResearchDisclosure, UUID> {

    @WorkspaceAgnostic("The instance-admin removal manifest spans every workspace's open disclosures")
    @Query("SELECT d FROM ResearchDisclosure d WHERE d.closedAt IS NULL ORDER BY d.createdAt, d.id")
    List<ResearchDisclosure> findOpen();

    @WorkspaceAgnostic("Instance-admin removal and closure address a disclosure by its dataset id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ResearchDisclosure d WHERE d.id = :id")
    Optional<ResearchDisclosure> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT count(d) > 0 FROM ResearchDisclosure d WHERE d.workspaceId = :workspaceId AND d.closedAt IS NULL")
    boolean hasOpen(@Param("workspaceId") long workspaceId);

    /** Closed headers hold no subject identity; workspace purge is the only thing that removes them. */
    @Modifying
    @Query("DELETE FROM ResearchDisclosure d WHERE d.workspaceId = :workspaceId AND d.closedAt IS NOT NULL")
    int deleteClosed(@Param("workspaceId") long workspaceId);

    @Modifying
    @Query(
            value =
                    "UPDATE research_disclosure SET created_by_account_id = NULL WHERE created_by_account_id = :accountId",
            nativeQuery = true)
    @WorkspaceAgnostic("Account erasure clears the releasing administrator in every workspace")
    int unlinkCreator(@Param("accountId") long accountId);
}
