package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TeamV2InfoDTOConverter implements Converter<TeamV2, TeamV2InfoDTO> {

    @Override
    public TeamV2InfoDTO convert(@NonNull TeamV2 source) {
        return new TeamV2InfoDTO(
            source.getId(),
            source.getNodeId(),
            source.getSlug(),
            source.getName(),
            source.getParentId(),
            source.getDescription(),
            source.getPrivacy(),
            source.getOrganization(),
            source.getHtmlUrl(),
            source.getLastSyncedAt(),
            source.getMemberships().size(),
            source.getRepoPermissions().size()
        );
    }
}
