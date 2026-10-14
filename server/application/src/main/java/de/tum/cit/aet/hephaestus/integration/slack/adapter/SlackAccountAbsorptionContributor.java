package de.tum.cit.aet.hephaestus.integration.slack.adapter;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SlackAccountAbsorptionContributor implements AccountAbsorptionContributor {
    private final NamedParameterJdbcTemplate jdbc;

    public SlackAccountAbsorptionContributor(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    @WorkspaceAgnostic("Verified account absorption moves that account across all its workspace source references")
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        Map<String, Long> ids = Map.of("claimed", claimedAccountId, "unclaimed", unclaimedAccountId);
        jdbc.update("UPDATE slack_message SET author_account_id=:claimed WHERE author_account_id=:unclaimed", ids);
        jdbc.update("""
            UPDATE slack_thread SET participant_account_ids=ARRAY(
                SELECT DISTINCT CASE WHEN account_id=:unclaimed THEN :claimed ELSE account_id END
                FROM unnest(participant_account_ids) account_id ORDER BY 1)
            WHERE :unclaimed=ANY(participant_account_ids)
            """, ids);
    }

    @Override
    @WorkspaceAgnostic("Account absorption validates every moved Slack reference before source memberships are removed")
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        Long remaining = jdbc.queryForObject("""
            SELECT (SELECT count(*) FROM slack_message WHERE author_account_id=:unclaimed)
              + (SELECT count(*) FROM slack_thread WHERE :unclaimed=ANY(participant_account_ids))
            """, Map.of("unclaimed", unclaimedAccountId), Long.class);
        if (remaining == null || remaining != 0)
            throw new IllegalStateException("Slack account references remain after absorption");
    }
}
