package de.tum.cit.aet.hephaestus.practices.review.dto;

import java.util.List;

public record ReviewRuntimeDTO(List<ReviewJobDTO> jobs) {
    public ReviewRuntimeDTO {
        jobs = List.copyOf(jobs);
    }
}
