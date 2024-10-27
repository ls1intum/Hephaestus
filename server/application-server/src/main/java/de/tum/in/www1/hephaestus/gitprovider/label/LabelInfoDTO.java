package de.tum.in.www1.hephaestus.gitprovider.label;

import org.springframework.lang.NonNull;

public record LabelInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String color) {

    public static LabelInfoDTO fromLabel(Label label) {
        return new LabelInfoDTO(
                label.getId(),
                label.getName(),
                label.getColor());
    }
}