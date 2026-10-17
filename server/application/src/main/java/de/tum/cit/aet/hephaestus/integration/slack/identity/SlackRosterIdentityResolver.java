package de.tum.cit.aet.hephaestus.integration.slack.identity;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipRoster;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Called only after the provider adapter confirms a human sender and source consent. */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackRosterIdentityResolver {
    private final GitProviderRegistry providers;
    private final PeopleRegistry people;
    private final MembershipRoster memberships;
    private final SlackMessageService slack;
    private final PersonProcessingSuppression suppression;

    public SlackRosterIdentityResolver(
            GitProviderRegistry providers,
            PeopleRegistry people,
            MembershipRoster memberships,
            SlackMessageService slack,
            PersonProcessingSuppression suppression) {
        this.providers = providers;
        this.people = people;
        this.memberships = memberships;
        this.slack = slack;
        this.suppression = suppression;
    }

    @Transactional
    public Optional<Long> humanAccountId(long workspaceId, String teamId, String subject) {
        if (teamId.isBlank() || subject.isBlank()) return Optional.empty();
        long providerId = providers.resolveProviderId("SLACK", "https://slack.com");
        if (suppression.isSuppressed(providerId, subject, teamId)) return Optional.empty();
        var name = slack.humanDisplayName(workspaceId, teamId, subject);
        if (name.isEmpty()) return Optional.empty();
        long accountId = people.ensureHuman(new PersonIdentity(providerId, subject, teamId), name.get());
        return memberships.ensureMember(workspaceId, accountId) ? Optional.of(accountId) : Optional.empty();
    }
}
