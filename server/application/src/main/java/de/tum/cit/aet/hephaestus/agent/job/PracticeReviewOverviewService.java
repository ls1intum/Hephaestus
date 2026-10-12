package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.StatusBucketCount;
import de.tum.cit.aet.hephaestus.core.time.TimeBuckets;
import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.BucketFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.PracticeFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.PracticeResultCounts;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPracticeGroupDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewResultCountsDTO;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How practice reviews went in a time range: reviews by {@code created_at}, results by {@code observed_at} and
 * feedback by {@code created_at}, the columns the admin lists filter by. Every total therefore matches the list
 * filtered to the same range and value, and each practice's result and feedback counts match the list
 * filtered to the same range, practice and value.
 */
@Service
@RequiredArgsConstructor
class PracticeReviewOverviewService {

    private final AgentJobRepository agentJobRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackRepository feedbackRepository;

    /**
     * All of a workspace's practice reviews as a range: from the first one's request until {@code to}, or now when
     * absent or later. With no review yet, or none before {@code to}, the range is empty, starting and ending at
     * {@code to}. It is not capped in length, since it is bounded by the workspace's own history.
     */
    @Transactional(readOnly = true)
    public TimeRange allTime(long workspaceId, @Nullable Instant to, Clock clock) {
        Instant now = clock.instant();
        Instant end = to == null || to.isAfter(now) ? now : to;
        Instant start = agentJobRepository
                .findFirstCreatedAt(workspaceId, AgentPurpose.PRACTICE_REVIEW)
                .filter(first -> first.isBefore(end))
                .orElse(end);
        return new TimeRange(start, end);
    }

    @Transactional(readOnly = true)
    public PracticeReviewOverviewDTO overview(long workspaceId, TimeBuckets buckets) {
        TimeRange range = buckets.range();
        int size = buckets.starts().size();
        long[] reviewsByBucket = new long[size];
        long[] practiceResultsByBucket = new long[size];
        long[] feedbackByBucket = new long[size];
        Map<AgentJobStatus, Long> reviews = new EnumMap<>(AgentJobStatus.class);
        ReviewFeedbackCountsDTO feedback = ReviewFeedbackCountsDTO.empty();
        if (size > 0) {
            Long[] starts = buckets.epochSeconds();
            for (StatusBucketCount row : agentJobRepository.countByBucketAndStatus(
                    workspaceId, AgentPurpose.PRACTICE_REVIEW, range.from(), range.to(), starts)) {
                reviewsByBucket[row.getBucket() - 1] += row.getCount();
                reviews.merge(row.getStatus(), row.getCount(), Long::sum);
            }
            for (var row :
                    practiceResultRepository.countResultsByBucket(workspaceId, range.from(), range.to(), starts)) {
                practiceResultsByBucket[row.getBucket() - 1] += row.getCount();
            }
            for (BucketFeedbackCounts row :
                    feedbackRepository.summarizeFeedbackByBucket(workspaceId, range.from(), range.to(), starts)) {
                feedbackByBucket[row.getBucket() - 1] += row.getTotal();
                feedback = feedback.plus(ReviewFeedbackCountsDTO.from(row));
            }
        }

        Map<Long, PracticeFeedbackCounts> practiceFeedback =
                feedbackRepository.summarizeFeedbackByPractice(workspaceId, range.from(), range.to()).stream()
                        .collect(Collectors.toMap(PracticeFeedbackCounts::getPracticeId, Function.identity()));
        List<PracticeResultCounts> practiceResults = practiceResultRepository.summarizeResultsByPractice(
                workspaceId, range.from(), range.to(), practiceFeedback.keySet().toArray(Long[]::new));
        List<PracticeReviewCountsDTO> practices = practiceResults.stream()
                .map(row -> new PracticeReviewCountsDTO(
                        row.getPracticeSlug(),
                        row.getPracticeName(),
                        ReviewPracticeGroupDTO.from(
                                row.getGroupSlug(), row.getGroupName(), row.getGroupIcon(), row.getGroupColor()),
                        ReviewResultCountsDTO.from(row),
                        row.getInvalidated(),
                        ReviewFeedbackCountsDTO.from(practiceFeedback.get(row.getPracticeId()))))
                .toList();

        return new PracticeReviewOverviewDTO(
                range.from(),
                range.to(),
                buckets.size(),
                ReviewRunCountsDTO.from(reviews),
                practices.stream()
                        .map(PracticeReviewCountsDTO::practiceResults)
                        .reduce(ReviewResultCountsDTO.empty(), ReviewResultCountsDTO::plus),
                practices.stream()
                        .mapToLong(PracticeReviewCountsDTO::practiceResultsInvalidated)
                        .sum(),
                feedback,
                IntStream.range(0, size)
                        .mapToObj(i -> new PracticeReviewBucketDTO(
                                buckets.starts().get(i),
                                reviewsByBucket[i],
                                practiceResultsByBucket[i],
                                feedbackByBucket[i]))
                        .toList(),
                practices);
    }
}
