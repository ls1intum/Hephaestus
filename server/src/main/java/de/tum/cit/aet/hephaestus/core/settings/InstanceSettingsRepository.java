package de.tum.cit.aet.hephaestus.core.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Singleton instance-wide settings row (id = 1) — no tenant dimension exists")
interface InstanceSettingsRepository extends JpaRepository<InstanceSettings, Long> {}
