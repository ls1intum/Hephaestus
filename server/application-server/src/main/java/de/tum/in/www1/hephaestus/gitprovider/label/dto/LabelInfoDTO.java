package de.tum.in.www1.hephaestus.gitprovider.label.dto;

import org.springframework.lang.NonNull;

public record LabelInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String color) {
}