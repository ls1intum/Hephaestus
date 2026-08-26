package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record ReviewRepositoryTarget(
    @NonNull @NotBlank @Size(max = 255) String nameWithOwner,
    @NonNull @NotNull List<String> baseBranches
) {
    public ReviewRepositoryTarget {
        nameWithOwner = validValue(nameWithOwner, "repository name");
        baseBranches = normalize(baseBranches);
    }

    private static List<String> normalize(List<String> values) {
        return java.util.Objects.requireNonNull(values, "baseBranches")
            .stream()
            .map(value -> validValue(value, "base branch"))
            .distinct()
            .sorted()
            .toList();
    }

    private static String validValue(String value, String label) {
        String normalized = java.util.Objects.requireNonNull(value, label).trim();
        requireValid(normalized, label);
        return normalized;
    }

    private static void requireValid(String value, String label) {
        if (value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(label + " must contain 1 to 255 characters");
        }
    }
}
