package de.tum.cit.aet.hephaestus.core.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@WorkspaceAgnostic("Singleton instance-wide settings row (id = 1) — no tenant dimension exists")
interface InstanceSettingsRepository extends JpaRepository<InstanceSettings, Long> {
    /**
     * Atomically create the singleton row if it is absent (brake released). {@code ON CONFLICT DO
     * NOTHING} is a no-op when a concurrent seed already inserted it, so this converges under a race
     * without aborting the transaction — unlike a plain {@code save} whose PK violation would poison it.
     */
    @Modifying
    @Query(
        value = "INSERT INTO instance_settings (id, silent_mode_engaged) VALUES (:id, false) ON CONFLICT (id) DO NOTHING",
        nativeQuery = true
    )
    void insertSingletonIfAbsent(long id);
}
