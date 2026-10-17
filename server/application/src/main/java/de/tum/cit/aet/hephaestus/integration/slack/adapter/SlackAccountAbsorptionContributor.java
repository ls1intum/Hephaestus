package de.tum.cit.aet.hephaestus.integration.slack.adapter;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class SlackAccountAbsorptionContributor implements AccountAbsorptionContributor {
    private final SlackMessageRepository messages;
    private final SlackThreadRepository threads;

    public SlackAccountAbsorptionContributor(SlackMessageRepository messages, SlackThreadRepository threads) {
        this.messages = messages;
        this.threads = threads;
    }

    @Override
    @WorkspaceAgnostic("Verified account absorption moves that account across all its workspace source references")
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        messages.absorbAuthorAccount(claimedAccountId, unclaimedAccountId);
        threads.absorbParticipantAccount(claimedAccountId, unclaimedAccountId);
    }

    @Override
    @WorkspaceAgnostic("Account absorption validates every moved Slack reference before source memberships are removed")
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (messages.existsByAuthorAccountId(unclaimedAccountId) || threads.containsAccount(unclaimedAccountId))
            throw new IllegalStateException("Slack account references remain after absorption");
    }
}
