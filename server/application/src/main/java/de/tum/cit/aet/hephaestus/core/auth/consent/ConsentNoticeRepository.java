package de.tum.cit.aet.hephaestus.core.auth.consent;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Consent notices are deployment-wide legal artifacts")
interface ConsentNoticeRepository extends JpaRepository<ConsentNotice, String> {}
