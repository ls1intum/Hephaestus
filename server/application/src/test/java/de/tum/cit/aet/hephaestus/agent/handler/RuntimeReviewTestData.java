package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobInput;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeGuidance;
import de.tum.cit.aet.hephaestus.practices.PracticeReadSource;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.ReviewedBy;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewMethod;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

public final class RuntimeReviewTestData {
    public static final UUID REVIEW = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final ItemRef ITEM = new ItemRef(1, 2, new ItemKind("docs.document"), 20L, null, null);

    private RuntimeReviewTestData() {}

    public static PracticeDefinition definition(@Nullable AboutRole about, @Nullable String script) {
        return new PracticeDefinition(
                "Clear names",
                new ReviewOn(List.of(), Map.of()),
                null,
                about,
                new PracticeReads(
                        "2.0.0",
                        List.of(new PracticeReadSource(new SourceKind("docs.document.core"), ReadNeed.REQUIRED))),
                new ReviewedBy(ReviewedByKind.HEPHAESTUS, null, List.of()),
                "Use clear names.",
                script,
                null,
                true,
                new PracticeGuidance(null, null, null, null, null));
    }

    public static ReviewAssignmentSnapshot assignment(@Nullable Long developer, @Nullable String script) {
        var method = new ReviewMethod(
                1,
                "b".repeat(64),
                List.of(
                        new BoundModelSlot("review", ModelKind.CHAT, null),
                        new BoundModelSlot("chat", ModelKind.CHAT, null),
                        new BoundModelSlot("decision", ModelKind.DECISION, null),
                        new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                        new BoundModelSlot("reranking", ModelKind.RERANKING, null)));
        var practice = new PinnedPractice(
                31, 32, definition(developer == null ? null : AboutRole.AUTHOR, script), "a".repeat(64), method);
        return new ReviewAssignmentSnapshot(
                new ReviewAssignmentId(REVIEW, 31, 2),
                ITEM,
                new ItemVersion("docs:one", null),
                developer,
                "clear-names",
                practice);
    }

    public static ReviewSnapshot review(ReviewKind kind, ReviewAssignmentSnapshot assignment) {
        return new ReviewSnapshot(
                REVIEW, 1, ITEM, kind, assignment.version(), ReviewOrigin.REQUEST, 1, List.of(assignment.practice()));
    }

    public static ReviewJob job() {
        var job = new ReviewJob();
        job.setId(UUID.randomUUID());
        job.setReviewId(REVIEW);
        job.setWorkspaceId(1);
        job.setAvailableAt(Instant.now());
        job.setInput(new ReviewJobInput(2, List.of(new ReviewAssignmentId(REVIEW, 31, 2))));
        job.setConfigSnapshot(new JsonMapper().createObjectNode());
        job.prePersist();
        return job;
    }
}
