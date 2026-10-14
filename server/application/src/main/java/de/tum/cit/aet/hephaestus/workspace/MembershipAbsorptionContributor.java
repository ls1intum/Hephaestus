package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class MembershipAbsorptionContributor implements AccountAbsorptionContributor {
    private final JdbcTemplate jdbc;

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        jdbc.query(
                "SELECT workspace_id,account_id FROM membership WHERE account_id IN (?,?) ORDER BY"
                        + " workspace_id,account_id FOR UPDATE",
                rs -> {},
                claimedAccountId,
                unclaimedAccountId);
        jdbc.update("""
            INSERT INTO membership(workspace_id,account_id,role,hidden,review_selected,onboarding_seen_revision,
                public_activity_seen,joined_at,left_at)
            SELECT workspace_id,?,role,hidden,review_selected,onboarding_seen_revision,public_activity_seen,joined_at,left_at
            FROM membership WHERE account_id=?
            ON CONFLICT(workspace_id,account_id) DO UPDATE SET
                role=CASE WHEN membership.role='OWNER' OR EXCLUDED.role='OWNER' THEN 'OWNER'
                    WHEN membership.role='ADMIN' OR EXCLUDED.role='ADMIN' THEN 'ADMIN' ELSE 'MEMBER' END,
                hidden=membership.hidden OR EXCLUDED.hidden,
                review_selected=membership.review_selected OR EXCLUDED.review_selected,
                onboarding_seen_revision=GREATEST(membership.onboarding_seen_revision,EXCLUDED.onboarding_seen_revision),
                public_activity_seen=membership.public_activity_seen OR EXCLUDED.public_activity_seen,
                joined_at=LEAST(membership.joined_at,EXCLUDED.joined_at),
                left_at=CASE WHEN membership.left_at IS NULL OR EXCLUDED.left_at IS NULL THEN NULL
                    ELSE GREATEST(membership.left_at,EXCLUDED.left_at) END
            """, claimedAccountId, unclaimedAccountId);
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        jdbc.update("DELETE FROM membership WHERE account_id=?", unclaimedAccountId);
    }
}
