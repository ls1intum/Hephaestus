package de.tum.cit.aet.hephaestus.workspace.settings;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ReviewRepositoryTarget(@NonNull String nameWithOwner, @NonNull List<String> baseBranches) {
    public ReviewRepositoryTarget {
        nameWithOwner = nameWithOwner == null ? "" : nameWithOwner.trim();
        baseBranches = normalize(baseBranches);
    }

    private static List<String> normalize(@Nullable List<String> values) {
        if (values == null) return List.of();
        return values
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
