package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileReviewActivityDTO;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Assembles profile review activity DTOs from git provider entities and pre-computed XP.
 *
 * <p>This assembler lives in the profile module (not gitprovider) because:
 * <ul>
 *   <li>It composes data from multiple sources (git entities + activity ledger XP)</li>
 *   <li>XP/score is a Hephaestus domain concept unknown to the gitprovider ETL layer</li>
 *   <li>The profile module owns the view-layer composition</li>
 * </ul>
 *
 * <p>Architecture:
 * <pre>
 * gitprovider (ETL)     →    activity (XP source)    →    profile (composition)
 * ────────────────           ──────────────────           ─────────────────────
 * PullRequestReview          activity_event.xp            ProfileReviewActivityDTO
 * IssueComment               (pre-computed)               (review + xp)
 * </pre>
 */
@Component
public class ProfileReviewActivityAssembler {

    /**
     * Assemble a profile review DTO from a PullRequestReview entity and pre-computed XP.
     *
     * @param review the git provider review entity
     * @param xp pre-computed XP from activity_event ledger
     * @return assembled profile DTO
     */
    public ProfileReviewActivityDTO assemble(@NonNull PullRequestReview review, int xp) {
        return new ProfileReviewActivityDTO(
            review.getId(),
            review.isDismissed(),
            review.getState(),
            review.getComments().size(),
            UserInfoDTO.fromUser(review.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(review.getPullRequest()),
            review.getHtmlUrl(),
            xp,
            review.getSubmittedAt()
        );
    }

    /**
     * Assemble a profile review DTO from an IssueComment entity and pre-computed XP.
     *
     * <p>Issue comments on PRs are treated as review activity with COMMENTED state.
     *
     * @param comment the git provider issue comment entity
     * @param xp pre-computed XP from activity_event ledger
     * @return assembled profile DTO
     */
    public ProfileReviewActivityDTO assemble(@NonNull IssueComment comment, int xp) {
        return new ProfileReviewActivityDTO(
            comment.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            0,
            UserInfoDTO.fromUser(comment.getAuthor()),
            PullRequestBaseInfoDTO.fromIssue(comment.getIssue()),
            comment.getHtmlUrl(),
            xp,
            comment.getCreatedAt()
        );
    }
}
