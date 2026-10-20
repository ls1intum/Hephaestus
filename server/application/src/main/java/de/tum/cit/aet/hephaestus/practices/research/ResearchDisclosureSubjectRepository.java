package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ResearchDisclosureSubjectRepository extends JpaRepository<ResearchDisclosureSubject, UUID> {

    @Query("""
            SELECT s FROM ResearchDisclosureSubject s
             WHERE s.workspaceId = :workspaceId AND s.disclosureId = :disclosureId
             ORDER BY s.participantKey
            """)
    List<ResearchDisclosureSubject> findForDisclosure(
            @Param("workspaceId") long workspaceId, @Param("disclosureId") UUID disclosureId);

    @WorkspaceAgnostic("The instance-admin removal manifest reads the subjects of every open disclosure at once")
    @Query("SELECT s FROM ResearchDisclosureSubject s WHERE s.disclosureId IN :disclosureIds ORDER BY s.participantKey")
    List<ResearchDisclosureSubject> findForDisclosures(@Param("disclosureIds") Collection<UUID> disclosureIds);

    @Modifying
    @Query(
            "DELETE FROM ResearchDisclosureSubject s WHERE s.workspaceId = :workspaceId AND s.disclosureId = :disclosureId")
    int deleteForDisclosure(@Param("workspaceId") long workspaceId, @Param("disclosureId") UUID disclosureId);

    /**
     * Erasure drops the account link and records why removal is owed, unless an earlier reason
     * stands. Rows already removed downstream have no link left to drop.
     */
    @Modifying
    @Query(value = """
                    UPDATE research_disclosure_subject
                       SET account_id = NULL,
                           removal_required_at = COALESCE(removal_required_at, now()),
                           removal_reason = COALESCE(removal_reason, 'ACCOUNT_ERASED')
                     WHERE account_id = :accountId
                    """, nativeQuery = true)
    @WorkspaceAgnostic("Account erasure reaches the person's disclosures in every workspace")
    int unlinkErasedAccount(@Param("accountId") long accountId);
}
