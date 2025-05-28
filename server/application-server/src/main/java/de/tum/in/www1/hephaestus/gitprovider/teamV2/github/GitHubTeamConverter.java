package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.kohsuke.github.GHTeam;
import org.springframework.stereotype.Component;

@Component
public class GitHubTeamConverter {

    public TeamV2 create(GHTeam src, String orgLogin) throws IOException {
        TeamV2 t = new TeamV2();
        t.setId(src.getId());
        t.setCreatedAt(DateUtil.convertToOffsetDateTime(src.getCreatedAt()));
        update(src, orgLogin, t);
        return t;
    }

    public void update(GHTeam src, String orgLogin, TeamV2 team) throws IOException {
        team.setName(src.getName());
        team.setSlug(src.getSlug());
        team.setDescription(src.getDescription());
        team.setPrivacy(src.getPrivacy() != null ? src.getPrivacy().name() : null);
        team.setGithubOrganization(orgLogin);
        team.setLastSyncedAt(OffsetDateTime.now());
        team.setUpdatedAt(DateUtil.convertToOffsetDateTime(src.getUpdatedAt()));
    }
}
