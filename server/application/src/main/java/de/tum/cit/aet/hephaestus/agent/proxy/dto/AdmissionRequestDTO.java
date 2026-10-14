package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AdmissionRequestDTO(
        int contractVersion, UUID reviewId, UUID jobId, int attempt, List<SubmittedResultDTO> results) {
    public AdmissionRequestDTO {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(jobId, "jobId");
        results = List.copyOf(results);
        if (contractVersion != 3) throw new IllegalArgumentException("Unsupported practice script contract");
        if (attempt < 0) throw new IllegalArgumentException("Attempt must not be negative");
    }
}
