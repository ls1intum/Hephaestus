package de.tum.cit.aet.hephaestus.practices.areadetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "A page of visible review runs")
public record PracticeAreaReviewHistoryPageDTO(
    @NonNull List<PracticeAreaReviewRunDTO> content,
    int page,
    int size,
    boolean hasNext
) {}
