package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import java.time.OffsetDateTime;
import org.kohsuke.github.GHTeam;
import org.springframework.stereotype.Component;

@Component
public class GitHubTeamConverter {

    public TeamV2 create(GHTeam src, String orgLogin) {
        TeamV2 t = new TeamV2();
        t.setId(src.getId());
        t.setCreatedAt(OffsetDateTime.now());
        update(src, orgLogin, t);
        return t;
    }

    public void update(GHTeam src, String orgLogin, TeamV2 t) {
        t.setName(src.getName());
        t.setSlug(src.getSlug());
        t.setDescription(src.getDescription());
        t.setPrivacy(src.getPrivacy() != null ? src.getPrivacy().name() : null);
        t.setGithubOrganization(orgLogin);
        t.setLastSyncedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
    }
}
