package de.tum.cit.aet.hephaestus.integration.outline.identity;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipRoster;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true")
public class OutlineRosterIdentityResolver {
    private final ConnectionService connections;
    private final GitProviderRegistry providers;
    private final PeopleRegistry people;
    private final MembershipRoster memberships;

    public OutlineRosterIdentityResolver(
            ConnectionService connections,
            GitProviderRegistry providers,
            PeopleRegistry people,
            MembershipRoster memberships) {
        this.connections = connections;
        this.providers = providers;
        this.people = people;
        this.memberships = memberships;
    }

    public void recordAuthors(OutlineDocument document) {
        var connection = connections
                .findInWorkspace(document.getWorkspaceId(), document.getConnectionId())
                .orElseThrow();
        if (!(connection.getConfig() instanceof ConnectionConfig.OutlineConfig config))
            throw new IllegalArgumentException("Document requires an Outline connection");
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank())
            throw new IllegalArgumentException("Outline server URL is required");
        long providerId = providers.resolveProviderId("OUTLINE", serverUrl);
        Map<String, String> authors = new LinkedHashMap<>();
        add(authors, document.getCreatedBySubject(), document.getCreatedByName());
        add(authors, document.getUpdatedBySubject(), document.getUpdatedByName());
        authors.forEach((subject, name) -> {
            long accountId =
                    people.ensureHuman(new PersonIdentity(providerId, subject, connection.getInstanceKey()), name);
            memberships.ensureMember(document.getWorkspaceId(), accountId);
        });
    }

    private static void add(Map<String, String> authors, @Nullable String subject, @Nullable String name) {
        if (subject != null && !subject.isBlank() && name != null && !name.isBlank())
            authors.putIfAbsent(subject, name);
    }
}
