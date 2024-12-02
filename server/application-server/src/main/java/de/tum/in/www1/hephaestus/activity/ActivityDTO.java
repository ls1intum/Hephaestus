package de.tum.in.www1.hephaestus.activity;

import io.micrometer.common.lang.NonNull;

import java.util.List;

public record ActivityDTO(
        @NonNull List<PullRequestWithBadPracticesDTO> pullRequests) {
}
