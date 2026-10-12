package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout.REPO_MOUNT_RELATIVE;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedProblem;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.WithheldResult;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * A complete body is admitted only when every contributor passes public evidence and delivery gates.
 * Supporting results and all public bodies must address one person; removing support cannot repair prose.
 */
final class ComposedReviewAdmission {

    private static final Logger log = LoggerFactory.getLogger(ComposedReviewAdmission.class);

    private ComposedReviewAdmission() {}

    record Admission(
            @Nullable DeliveryContent content,
            boolean needsApproval,
            @Nullable Long recipient) {}

    /**
     * @param recorded every result the review recorded, stamped with its persisted identities
     * @param publicSubjects the person each publicly eligible result is about, by result id ({@link PublicReviewEligibility})
     * @param automatic the results admitted to the work without approval
     * @param awaitingApproval the results admitted to the work only once a reviewer approves
     */
    static Admission admit(
            ComposedReview review,
            ArtifactKind artifact,
            List<ValidatedResult> recorded,
            Map<UUID, Long> publicSubjects,
            List<ValidatedResult> automatic,
            List<ValidatedResult> awaitingApproval) {
        Map<String, ValidatedResult> byId = new HashMap<>();
        for (ValidatedResult practiceResult : recorded) {
            UUID id = practiceResult.practiceResultId();
            if (id != null) byId.put(id.toString(), practiceResult);
        }
        Set<String> automaticIds = idsOf(automatic);
        Set<String> approvalIds = idsOf(awaitingApproval);
        Set<Long> recipients = new HashSet<>();
        boolean needsApproval = false;

        String summary = null;
        List<String> summaryContributors = List.of();
        List<String> summaryObservations = List.of();
        ComposedReview.Summary composedSummary = review.summary();
        if (composedSummary != null) {
            Part part = PullRequestCommentPoster.speaksApproval(composedSummary.body())
                    ? null
                    : partOf(composedSummary.basedOn(), byId, publicSubjects, automaticIds, approvalIds);
            if (part != null && ownsProblems(part.support(), composedSummary.observations())) {
                summary = composedSummary.body();
                summaryContributors = keysOf(part.support());
                summaryObservations = composedSummary.observations();
                recipients.add(part.recipient());
                needsApproval = part.awaitsApproval();
            } else {
                log.info("The summary speaks approval or rests on a result that may not go out; it stays unsaid");
            }
        }

        List<DiffNote> notes = new ArrayList<>();
        for (ComposedReview.InlineNote note : review.inline()) {
            if (!ArtifactKinds.hasInlineLane(artifact)) {
                log.warn("A line note was composed for work without lines; it stays unsaid");
                continue;
            }
            Part part = PullRequestCommentPoster.speaksApproval(note.body())
                    ? null
                    : partOf(note.basedOn(), byId, publicSubjects, automaticIds, approvalIds);
            if (part == null
                    || part.support().stream()
                            .anyMatch(practiceResult ->
                                    practiceResult.deliveryBehavior().summaryOnly())) {
                log.info(
                        "A line note violates placement policy, speaks approval or rests on a result that may not go out; it stays unsaid");
                continue;
            }
            ValidatedResult anchored = byId.get(note.anchor().practiceResultId());
            ValidatedProblem problem = anchored == null
                    ? null
                    : anchored.problem(UUID.fromString(note.anchor().observationId()))
                            .orElse(null);
            if (anchored == null
                    || problem == null
                    || !note.basedOn().contains(note.anchor().practiceResultId())
                    || !ownsProblems(part.support(), note.observations())
                    || !verifiedAnchor(problem, note.anchor())) {
                log.warn("A line note's citation is not a verified line of this change; the note stays unsaid");
                continue;
            }
            List<String> named = new ArrayList<>();
            named.add(note.anchor().observationId());
            note.observations().stream()
                    .filter(id -> !id.equals(note.anchor().observationId()))
                    .forEach(named::add);
            notes.add(new DiffNote(
                    repoRelative(note.anchor().path()),
                    note.anchor().startLine(),
                    note.anchor().endLine(),
                    note.body(),
                    deliveryKey(anchored, problem, note.anchor().citationIndex()),
                    keysOf(part.support()),
                    List.copyOf(named)));
            recipients.add(part.recipient());
            needsApproval |= part.awaitsApproval();
        }

        if (recipients.size() > 1) {
            log.warn("The parts of a composed review are about different people; nothing of it reaches the work");
            summary = null;
            summaryContributors = List.of();
            summaryObservations = List.of();
            notes.clear();
            recipients.clear();
            needsApproval = false;
        }

        List<WithheldResult> withheld = new ArrayList<>();
        Set<String> withheldKeys = new HashSet<>();
        for (ComposedReview.Withheld decision : review.withheld()) {
            List<ValidatedResult> support = resolve(decision.basedOn(), byId);
            if (support.isEmpty()
                    || support.size() != decision.basedOn().size()
                    || support.stream()
                            .anyMatch(practiceResult -> practiceResult.outcome() != Outcome.NOT_MET
                                    || practiceResult.occurrenceKey() == null
                                    || practiceResult.occurrenceKey().isBlank()
                                    || practiceResult.practiceResultId() == null
                                    || !publicSubjects.containsKey(practiceResult.practiceResultId()))) {
                continue;
            }
            for (ValidatedResult practiceResult : support) {
                String key = practiceResult.occurrenceKey();
                if (practiceResult.outcome() == Outcome.NOT_MET && key != null && withheldKeys.add(key)) {
                    withheld.add(new WithheldResult(key, FeedbackSuppressionReason.COMPOSER_WITHHELD));
                }
            }
        }

        DeliveryContent content = summary == null && notes.isEmpty() && withheld.isEmpty()
                ? null
                : new DeliveryContent(
                        summary,
                        List.copyOf(notes),
                        List.copyOf(withheld),
                        summaryContributors,
                        null,
                        summaryObservations);
        return new Admission(
                content,
                needsApproval,
                recipients.isEmpty() ? null : recipients.iterator().next());
    }

    /** One admitted part: the results it rests on, the person they are about, and whether it waits. */
    private record Part(List<ValidatedResult> support, Long recipient, boolean awaitsApproval) {}

    /**
     * A part rests on what it names, or it does not go out. A result that decides nothing — not applicable,
     * undetermined — cannot carry a claim about the work, and one that is not about the work's author or is drawn from
     * the person's history may not appear on it ({@link PublicReviewEligibility}), so a part naming either is refused
     * like one the gates held.
     */
    private static @Nullable Part partOf(
            List<String> basedOn,
            Map<String, ValidatedResult> byId,
            Map<UUID, Long> publicSubjects,
            Set<String> automaticIds,
            Set<String> approvalIds) {
        if (basedOn.isEmpty()) return null;
        List<ValidatedResult> support = new ArrayList<>(basedOn.size());
        Set<Long> about = new HashSet<>();
        boolean approval = false;
        for (String id : basedOn) {
            ValidatedResult practiceResult = byId.get(id);
            if (practiceResult == null
                    || practiceResult.occurrenceKey() == null
                    || practiceResult.occurrenceKey().isBlank()
                    || practiceResult.outcome() != Outcome.NOT_MET) return null;
            Long subject = publicSubjects.get(UUID.fromString(id));
            if (subject == null) return null;
            about.add(subject);
            if (!automaticIds.contains(id)) {
                if (!approvalIds.contains(id)) return null;
                approval = true;
            }
            support.add(practiceResult);
        }
        if (support.isEmpty() || about.size() != 1) {
            if (about.size() > 1) log.warn("A composed part rests on results about different people");
            return null;
        }
        return new Part(List.copyOf(support), about.iterator().next(), approval);
    }

    /** Whether every named problem belongs to one of the results the part rests on. */
    private static boolean ownsProblems(List<ValidatedResult> support, List<String> observations) {
        Set<String> owned = new HashSet<>();
        for (ValidatedResult practiceResult : support) {
            for (ValidatedProblem problem : practiceResult.problems()) {
                if (problem.observationId() != null)
                    owned.add(problem.observationId().toString());
            }
        }
        return owned.containsAll(observations);
    }

    /** Only a problem's citation the admission verified at its exact diff location may carry a note onto that line. */
    private static boolean verifiedAnchor(ValidatedProblem problem, ComposedReview.ResolvedAnchor anchor) {
        JsonNode citations = problem.citations();
        if (!citations.isArray() || anchor.citationIndex() < 0 || anchor.citationIndex() >= citations.size()) {
            return false;
        }
        JsonNode citation = citations.get(anchor.citationIndex());
        JsonNode verification = citation.path("verification");
        Integer endLine = anchor.endLine();
        return citation.isObject()
                && "VERIFIED".equals(verification.path("status").asString())
                && "EXACT_LOCATION".equals(verification.path("scope").asString())
                && PracticePreconditionClause.DIFF_SOURCE
                        .value()
                        .equals(citation.path("sourceKind").asString())
                && "NEW".equals(citation.path("side").asString())
                && repoRelative(anchor.path())
                        .equals(repoRelative(citation.path("path").asString("")))
                && anchor.startLine() == citation.path("startLine").asInt()
                && (endLine == null || endLine == citation.path("endLine").asInt(anchor.startLine()));
    }

    /** One note per citation of a problem, so the key names the problem and the citation as well as the result. */
    private static @Nullable String deliveryKey(ValidatedResult anchored, ValidatedProblem problem, int citationIndex) {
        String key = anchored.occurrenceKey();
        return key == null ? null : "observation:" + key + ":" + problem.position() + ":" + citationIndex;
    }

    private static List<ValidatedResult> resolve(List<String> basedOn, Map<String, ValidatedResult> byId) {
        return basedOn.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static List<String> keysOf(List<ValidatedResult> practiceResults) {
        return practiceResults.stream()
                .map(ValidatedResult::occurrenceKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static Set<String> idsOf(List<ValidatedResult> practiceResults) {
        Set<String> ids = new HashSet<>();
        for (ValidatedResult practiceResult : practiceResults) {
            UUID id = practiceResult.practiceResultId();
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    private static String repoRelative(String path) {
        return path.startsWith(REPO_MOUNT_RELATIVE) ? path.substring(REPO_MOUNT_RELATIVE.length()) : path;
    }
}
