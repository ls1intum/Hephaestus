package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackDisagreementDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.OwnFeedbackCopyRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.OwnFeedbackRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewBoundFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.model.Problem;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.result.dto.EvidenceCitationDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ProblemDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ProblemSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultEvidenceDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultExampleDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ResultMarkDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ResultPresentation {
    private final ResultMarkRepository marks;
    private final ProblemRepository problems;
    private final ProductReferenceQuery references;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public String name(ResultView view) {
        return view.version() == null || view.version().getName() == null
                ? "Unknown practice version"
                : Objects.requireNonNull(view.version().getName());
    }

    public Currentness currentness(ResultView view) {
        return ResultVisibilityPolicy.currentness(
                view.result(), view.version(), view.practice().getCurrentVersion());
    }

    public @Nullable Instant markedAt(Result result, ResultMarkKind kind) {
        return marks
                .findByWorkspaceIdAndResultIdOrderByCreatedAtDescIdDesc(result.getWorkspaceId(), result.getId())
                .stream()
                .filter(m -> m.getKind() == kind && m.getRemovedAt() == null)
                .map(ResultMark::getCreatedAt)
                .findFirst()
                .orElse(null);
    }

    public List<Problem> problems(Result result) {
        return problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(result.getWorkspaceId(), result.getId());
    }

    public ProblemDTO problem(Problem child, boolean permitted) {
        return new ProblemDTO(
                child.getId(),
                child.getSeverity(),
                permitted ? child.getSummary() : "Evidence hidden",
                child.getResultId(),
                child.getPosition(),
                child.getKey(),
                permitted ? child.getExplanation() : null,
                permitted
                        ? json.convertValue(child.getCitations(), new TypeReference<List<EvidenceCitationDTO>>() {})
                        : List.of(),
                permitted && child.getBasis() != null ? json.convertValue(child.getBasis(), ClaimBasis.class) : null,
                permitted ? child.getNextStep() : null);
    }

    public List<ProblemSummaryDTO> summaries(Result result, boolean permitted) {
        return permitted
                ? problems(result).stream()
                        .map(p -> new ProblemSummaryDTO(p.getId(), p.getSeverity(), p.getSummary()))
                        .toList()
                : List.of();
    }

    public ResultListDTO list(ResultView v, boolean permitted) {
        var r = v.result();
        return new ResultListDTO(
                r.getId(),
                r.getReviewId(),
                v.item(),
                v.practice().getSlug(),
                name(v),
                r.getOutcome(),
                r.getSeverity(),
                permitted ? r.getSummary() : null,
                v.review().getOrigin(),
                r.getRecordedAt(),
                currentness(v),
                markedAt(r, ResultMarkKind.INCORRECT),
                markedAt(r, ResultMarkKind.ACCEPTED),
                summaries(r, permitted));
    }

    public ResultDetailDTO detail(ResultView v, boolean permitted) {
        var r = v.result();
        return new ResultDetailDTO(
                r.getId(),
                r.getReviewId(),
                v.item(),
                v.practice().getSlug(),
                name(v),
                r.getOutcome(),
                r.getSeverity(),
                permitted ? r.getSummary() : null,
                v.review().getOrigin(),
                r.getRecordedAt(),
                currentness(v),
                markedAt(r, ResultMarkKind.INCORRECT),
                markedAt(r, ResultMarkKind.ACCEPTED),
                r.getPracticeVersionId() == null
                        ? null
                        : r.getPracticeVersionId().toString(),
                v.version() == null ? null : v.version().getNumber(),
                r.getItemVersion(),
                permitted ? r.getExplanation() : null,
                evidence(r, permitted),
                problems(r).stream().map(p -> problem(p, permitted)).toList(),
                example(r, permitted),
                coverage(r, permitted),
                r.getEndedAt(),
                r.getEndReason(),
                ownFeedback(r));
    }

    public @Nullable ResultEvidenceDTO evidence(Result r, boolean permitted) {
        return permitted && r.getEvidence() != null
                ? json.convertValue(r.getEvidence(), ResultEvidenceDTO.class)
                : null;
    }

    public @Nullable ResultExampleDTO example(Result r, boolean permitted) {
        return permitted && r.getExample() != null ? json.convertValue(r.getExample(), ResultExampleDTO.class) : null;
    }

    public @Nullable ResultCoverage coverage(Result r, boolean permitted) {
        return permitted && r.getCoverage() != null ? json.convertValue(r.getCoverage(), ResultCoverage.class) : null;
    }

    public ResultMarkDTO mark(long ws, ResultMark m) {
        var ids = Stream.of(m.getCreatedById(), m.getRemovedById())
                .filter(Objects::nonNull)
                .toList();
        var accounts = references.accounts(ws, ids);
        return new ResultMarkDTO(
                m.getId(),
                m.getResultId(),
                m.getKind(),
                m.getReason(),
                accounts.get(m.getCreatedById()),
                m.getCreatedAt(),
                accounts.get(m.getRemovedById()),
                m.getRemovedAt(),
                m.getRemovalReason());
    }

    public List<ReviewBoundFeedbackDTO> boundFeedback(Result r) {
        return jdbc.sql("""
            SELECT DISTINCT f.id,f.destination,f.state,f.delivered_at,
              COALESCE((SELECT d.action='WITHDRAW' FROM feedback_decision d WHERE d.workspace_id=f.workspace_id AND d.feedback_id=f.id AND d.action IN ('WITHDRAW','RESTORE') ORDER BY d.decided_at DESC,d.id::text DESC LIMIT 1),false) AS withdrawn
            FROM feedback f JOIN feedback_basis b ON b.feedback_id=f.id AND b.workspace_id=f.workspace_id
            WHERE f.workspace_id=:ws AND b.result_id=:id ORDER BY f.id
            """)
                .param("ws", r.getWorkspaceId())
                .param("id", r.getId())
                .query((rs, n) -> new ReviewBoundFeedbackDTO(
                        rs.getObject("id", UUID.class),
                        FeedbackDestination.valueOf(rs.getString("destination")),
                        FeedbackState.valueOf(rs.getString("state")),
                        instant(rs.getObject("delivered_at", OffsetDateTime.class)),
                        rs.getBoolean("withdrawn")))
                .list();
    }

    public List<FeedbackDisagreementDTO> disagreements(Result r) {
        return jdbc.sql("""
            SELECT DISTINCT f.id,f.destination,s.comment,s.created_at FROM feedback f
            JOIN feedback_basis b ON b.feedback_id=f.id AND b.workspace_id=f.workspace_id JOIN LATERAL (SELECT r.* FROM feedback_response r WHERE r.feedback_id=f.id AND r.workspace_id=f.workspace_id ORDER BY r.created_at DESC,r.id::text DESC LIMIT 1) s ON true
            WHERE f.workspace_id=:ws AND b.result_id=:id AND s.resolution='DISAGREE' ORDER BY s.created_at
            """)
                .param("ws", r.getWorkspaceId())
                .param("id", r.getId())
                .query((rs, n) -> new FeedbackDisagreementDTO(
                        rs.getObject("id", UUID.class),
                        FeedbackDestination.valueOf(rs.getString("destination")),
                        rs.getString("comment"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public ReviewFeedbackCountsDTO feedbackCounts(Result r) {
        var c = new EnumMap<FeedbackState, Integer>(FeedbackState.class);
        boundFeedback(r).forEach(f -> c.merge(f.state(), 1, Integer::sum));
        return new ReviewFeedbackCountsDTO(
                c.getOrDefault(FeedbackState.AWAITING_APPROVAL, 0),
                c.getOrDefault(FeedbackState.READY, 0),
                c.getOrDefault(FeedbackState.DELIVERED, 0),
                c.getOrDefault(FeedbackState.UNCONFIRMED, 0),
                c.getOrDefault(FeedbackState.WITHHELD, 0),
                c.getOrDefault(FeedbackState.REJECTED, 0),
                c.getOrDefault(FeedbackState.REPLACED, 0),
                c.getOrDefault(FeedbackState.FAILED, 0));
    }

    public List<OwnFeedbackRefDTO> ownFeedback(Result r) {
        if (r.getDeveloperId() == null) return List.of();
        return jdbc.sql("""
            SELECT DISTINCT f.id,f.destination,f.state,f.delivered_at,s.resolution,s.usefulness,s.comment,s.created_at AS responded_at
            FROM feedback f JOIN feedback_basis b ON b.feedback_id=f.id AND b.workspace_id=f.workspace_id LEFT JOIN LATERAL (SELECT r.* FROM feedback_response r WHERE r.feedback_id=f.id AND r.workspace_id=f.workspace_id ORDER BY r.created_at DESC,r.id::text DESC LIMIT 1) s ON true
            WHERE f.workspace_id=:ws AND f.developer_id=:developer AND b.result_id=:id ORDER BY f.id
            """)
                .param("ws", r.getWorkspaceId())
                .param("developer", r.getDeveloperId())
                .param("id", r.getId())
                .query((rs, n) -> {
                    var id = rs.getObject("id", UUID.class);
                    var responded = rs.getObject("responded_at", OffsetDateTime.class);
                    var response = responded == null
                            ? null
                            : new FeedbackResponseDTO(
                                    rs.getString("resolution") == null
                                            ? null
                                            : FeedbackResolution.valueOf(rs.getString("resolution")),
                                    rs.getString("usefulness") == null
                                            ? null
                                            : FeedbackUsefulness.valueOf(rs.getString("usefulness")),
                                    rs.getString("comment"),
                                    responded.toInstant());
                    return new OwnFeedbackRefDTO(
                            id,
                            FeedbackDestination.valueOf(rs.getString("destination")),
                            FeedbackState.valueOf(rs.getString("state")),
                            instant(rs.getObject("delivered_at", OffsetDateTime.class)),
                            response,
                            ownCopies(r.getWorkspaceId(), id));
                })
                .list();
    }

    private List<OwnFeedbackCopyRefDTO> ownCopies(long ws, UUID feedback) {
        return jdbc.sql("SELECT id,kind,external_url,posted_at FROM feedback_copy WHERE workspace_id=:ws AND"
                        + " feedback_id=:id AND posted_at IS NOT NULL ORDER BY position,id")
                .param("ws", ws)
                .param("id", feedback)
                .query((rs, n) -> new OwnFeedbackCopyRefDTO(
                        rs.getObject("id", UUID.class),
                        FeedbackCopyKind.valueOf(rs.getString("kind")),
                        rs.getString("external_url"),
                        rs.getObject("posted_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
