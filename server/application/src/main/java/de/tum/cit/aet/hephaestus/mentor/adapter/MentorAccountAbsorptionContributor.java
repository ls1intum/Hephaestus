package de.tum.cit.aet.hephaestus.mentor.adapter;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Order(30)
@Component
@RequiredArgsConstructor
public class MentorAccountAbsorptionContributor implements AccountAbsorptionContributor {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        jdbc.update(
                "UPDATE chat_thread SET account_id=:claimed WHERE account_id=:unclaimed",
                Map.of("claimed", claimedAccountId, "unclaimed", unclaimedAccountId));
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        Long remaining = jdbc.queryForObject(
                "SELECT count(*) FROM chat_thread WHERE account_id=:unclaimed",
                Map.of("unclaimed", unclaimedAccountId),
                Long.class);
        if (remaining == null || remaining != 0) {
            throw new IllegalStateException("Mentor thread ownership did not move to the claimed account");
        }
    }
}
