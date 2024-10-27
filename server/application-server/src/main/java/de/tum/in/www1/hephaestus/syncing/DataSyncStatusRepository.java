package de.tum.in.www1.hephaestus.syncing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSyncStatusRepository extends JpaRepository<DataSyncStatus, Long> {

    public Optional<DataSyncStatus> findTopByOrderByStartTimeDesc();
}