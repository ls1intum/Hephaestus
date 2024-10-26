package de.tum.in.www1.hephaestus.gitprovider.user.dto;

import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Component
public class UserDTOConverter {
    
    public UserInfoDTO convertToDTO(User user) {
        return new UserInfoDTO(
            user.getId(),
            user.getLogin(),
            user.getAvatarUrl(),
            user.getName(),
            user.getHtmlUrl());
    }
}
