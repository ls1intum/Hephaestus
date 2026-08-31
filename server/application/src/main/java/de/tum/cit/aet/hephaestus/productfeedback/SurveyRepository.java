package de.tum.cit.aet.hephaestus.productfeedback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SurveyRepository extends JpaRepository<Survey, UUID> {
    @Query(
            "select s from Survey s where s.active = true and s.startsAt <= :now and (s.endsAt is null or s.endsAt > :now) and (s.workspaceId is null or s.workspaceId = :workspaceId) and not exists (select x from SurveySubmission x where x.surveyId = s.id and x.accountId = :accountId) order by s.createdAt")
    List<Survey> findAvailable(Long workspaceId, Long accountId, Instant now);
}
