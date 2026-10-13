package de.tum.cit.aet.hephaestus.activity.overview.dto;

import de.tum.cit.aet.hephaestus.activity.overview.ActivityKind;
import de.tum.cit.aet.hephaestus.activity.overview.dto.PublicActivityDTO.PublicActivityCountsDTO;
import de.tum.cit.aet.hephaestus.activity.overview.dto.PublicActivityDTO.PublicActivityRepositoryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** One published person's public contributions. Excludes account details, teams, automation and private activity. */
public record PublicActivityPersonDetailDTO(
        @NonNull Instant from,
        @NonNull Instant to,
        @NonNull PublicActivityPersonIdentityDTO person,
        @NonNull PublicActivityCountsDTO counts,
        @NonNull PublicActivityReviewBreakdownDTO breakdown,
        @Nullable Instant firstContributionAt,
        @NonNull List<PublicActivityPersonWeekDTO> weeks,
        @NonNull List<PublicActivityPersonRepositoryDTO> repositories,
        @NonNull List<ActivityKind> supportedKinds) {

    public static final List<ActivityKind> SUPPORTED_KINDS = List.of(
            ActivityKind.PULL_REQUEST_OPENED,
            ActivityKind.PULL_REQUEST_MERGED,
            ActivityKind.REVIEW_APPROVED,
            ActivityKind.REVIEW_CHANGES_REQUESTED,
            ActivityKind.REVIEW_COMMENTED,
            ActivityKind.ISSUE_OPENED);

    public static PublicActivityPersonDetailDTO from(ActivityPersonDetailDTO detail) {
        var person = detail.person();
        return new PublicActivityPersonDetailDTO(
                detail.from(),
                detail.to(),
                new PublicActivityPersonIdentityDTO(
                        person.login(), person.name(), person.avatarUrl(), person.htmlUrl()),
                PublicActivityCountsDTO.from(detail.counts()),
                new PublicActivityReviewBreakdownDTO(
                        detail.breakdown().approvals(),
                        detail.breakdown().changeRequests(),
                        detail.breakdown().commentReviews()),
                detail.firstContributionAt(),
                detail.weeks().stream()
                        .map(week -> new PublicActivityPersonWeekDTO(
                                week.start(),
                                PublicActivityCountsDTO.from(week.counts()),
                                new PublicActivityReviewBreakdownDTO(
                                        week.breakdown().approvals(),
                                        week.breakdown().changeRequests(),
                                        week.breakdown().commentReviews())))
                        .toList(),
                detail.repositories().stream()
                        .map(repository -> new PublicActivityPersonRepositoryDTO(
                                Long.toString(repository.repository().id()),
                                new PublicActivityRepositoryDTO(
                                        repository.repository().key(),
                                        repository.repository().name()),
                                PublicActivityCountsDTO.from(repository.counts())))
                        .toList(),
                SUPPORTED_KINDS);
    }

    public record PublicActivityPersonIdentityDTO(
            @NonNull String login,
            @NonNull String name,
            @NonNull String avatarUrl,
            @NonNull String htmlUrl) {}

    public record PublicActivityPersonWeekDTO(
            @NonNull Instant start,
            @NonNull PublicActivityCountsDTO counts,
            @NonNull PublicActivityReviewBreakdownDTO breakdown) {}

    public record PublicActivityReviewBreakdownDTO(
            @Schema(requiredMode = RequiredMode.REQUIRED) long approvals,
            @Schema(requiredMode = RequiredMode.REQUIRED) long changeRequests,
            @Schema(requiredMode = RequiredMode.REQUIRED) long commentReviews) {}

    public record PublicActivityPersonRepositoryDTO(
            @NonNull String rowKey,
            @NonNull PublicActivityRepositoryDTO repository,
            @NonNull PublicActivityCountsDTO counts) {}
}
