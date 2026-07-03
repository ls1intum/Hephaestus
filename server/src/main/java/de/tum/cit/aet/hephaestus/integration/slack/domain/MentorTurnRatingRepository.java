package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Scoped access to {@link MentorTurnRating} rows. Every finder carries the {@code workspace_id} predicate the
 * tenancy {@code StatementInspector} requires (the table is workspace-scoped by construction).
 */
@Repository
public interface MentorTurnRatingRepository extends JpaRepository<MentorTurnRating, UUID> {
    /**
     * The current thumb for one turn by one rater: the newest append-only row for
     * {@code (workspace, rater, message)}. This is the "latest wins" read — a later click supersedes an earlier
     * one purely by recency, with the full history retained.
     */
    Optional<MentorTurnRating> findFirstByWorkspaceIdAndRaterUserIdAndSlackMessageTsOrderByCreatedAtDescIdDesc(
        Long workspaceId,
        Long raterUserId,
        String slackMessageTs
    );

    /**
     * The rater's most recent thumb in a workspace, served by the {@code (rater_user_id, created_at DESC)} index —
     * the per-user recency scan.
     */
    Optional<MentorTurnRating> findFirstByWorkspaceIdAndRaterUserIdOrderByCreatedAtDescIdDesc(
        Long workspaceId,
        Long raterUserId
    );

    /** Workspace purge (S5, folded into {@code SlackWorkspacePurgeAdapter}). Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);
}
