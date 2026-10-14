package de.tum.cit.aet.hephaestus.core.privacy.spi;

import org.jspecify.annotations.Nullable;

/** An exact native subject in one provider instance; team scopes Slack subjects and retains verified Outline workspace keys. */
public record PersonIdentity(
        long providerId, String subject, @Nullable String teamId) {
    public PersonIdentity {
        if (providerId <= 0
                || subject.isBlank()
                || subject.length() > 255
                || (teamId != null && (teamId.isBlank() || teamId.length() > 255))) {
            throw new IllegalArgumentException("An exact provider id and native subject are required");
        }
    }

    public static PersonIdentity of(long providerId, String subject, String teamKey) {
        return new PersonIdentity(providerId, subject, teamKey.isEmpty() ? null : teamKey);
    }

    public String teamKey() {
        return teamKey(teamId);
    }

    public static String teamKey(@Nullable String teamId) {
        return teamId == null ? "" : teamId;
    }
}
