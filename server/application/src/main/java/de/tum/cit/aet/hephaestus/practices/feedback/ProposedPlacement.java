package de.tum.cit.aet.hephaestus.practices.feedback;

import org.jspecify.annotations.Nullable;

public record ProposedPlacement(
        PlacementType type,
        String body,
        @Nullable String path,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable String recurrenceKey) {
    public static ProposedPlacement summary(String body) {
        return new ProposedPlacement(PlacementType.SUMMARY, body, null, null, null, null);
    }

    public static ProposedPlacement inline(
            String body, String path, int startLine, @Nullable Integer endLine, @Nullable String recurrenceKey) {
        return new ProposedPlacement(PlacementType.INLINE, body, path, startLine, endLine, recurrenceKey);
    }
}
