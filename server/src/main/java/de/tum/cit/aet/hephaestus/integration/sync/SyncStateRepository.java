package de.tum.cit.aet.hephaestus.integration.sync;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncStateRepository extends JpaRepository<SyncState, Long> {

    Optional<SyncState> findByConnectionIdAndStreamName(long connectionId, String streamName);

    List<SyncState> findByConnectionId(long connectionId);

    @Query("SELECT s FROM SyncState s WHERE s.connection.workspace.id = :workspaceId")
    List<SyncState> findByWorkspaceId(@Param("workspaceId") long workspaceId);
}
