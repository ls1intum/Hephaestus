package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record ReviewSubjectDTO(
    @NonNull Long id,
    @NonNull @Schema(description = "Login on the source provider") String login,
    @Schema(description = "Display name, when known") String name,
    String avatarUrl
) {
    public static ReviewSubjectDTO from(User user) {
        return new ReviewSubjectDTO(user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl());
    }
}
