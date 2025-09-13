package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TeamInfoDTOConverter implements Converter<Team, TeamInfoDTO> {

    @Override
    public TeamInfoDTO convert(@NonNull Team source) {
        return new TeamInfoDTO(
            source.getId(),
            source.getName(),
            source.getParentId(),
            source.getDescription(),
            source.getPrivacy(),
            source.getOrganization(),
            source.getHtmlUrl(),
            source.isHidden(),
            source
                .getRepoPermissions()
                .stream()
                .map(rp -> RepositoryInfoDTO.fromRepository(rp.getRepository()))
                .toList(),
            source.getLabels().stream().map(LabelInfoDTO::fromLabel).toList(),
            source
                .getMemberships()
                .stream()
                .map(m -> m.getUser())
                .filter(u -> u != null && !User.Type.BOT.equals(u.getType()))
                .map(UserInfoDTO::fromUser)
                .toList(),
            source.getMemberships().size(),
            source.getRepoPermissions().size()
        );
    }
}
