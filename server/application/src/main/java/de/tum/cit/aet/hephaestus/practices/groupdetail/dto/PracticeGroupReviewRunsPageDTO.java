package de.tum.cit.aet.hephaestus.practices.groupdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "A page of visible review runs")
public record PracticeGroupReviewRunsPageDTO(
    @NonNull List<PracticeGroupReviewRunDTO> content,
    int page,
    int size,
    boolean hasNext
) {}
