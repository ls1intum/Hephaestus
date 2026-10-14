package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class ScmCaptureFixtures {
    private ScmCaptureFixtures() {}

    public static ScmCaptureContext context(JsonNode fixture, long workspaceId, boolean issue) {
        long source = fixture.path(issue ? "issue_id" : "pull_request_id").asLong(1);
        long repository = fixture.path("repository_id").asLong(1);
        Long review = fixture.path("review_id").isIntegralNumber()
                ? fixture.path("review_id").asLong()
                : null;
        Long reviewer = fixture.path("about_user_id").isIntegralNumber()
                ? fixture.path("about_user_id").asLong()
                : null;
        String head = fixture.path("commit_sha").asString("a".repeat(40));
        String base = fixture.path("base_ref_oid").asString("b".repeat(40));
        var work = new ScmCapturedWork(
                source,
                repository,
                fixture.path("pr_number").asInt(1),
                fixture.path("pr_url").asString("https://scm.example/owner/repo/pull/1"),
                fixture.path("repository_full_name").asString("owner/repo"),
                fixture.path("title").asString("Work"),
                fixture.path("body").isString() ? fixture.path("body").asString() : null,
                head,
                base,
                fixture.path("source_branch").asString("feature"),
                fixture.path("target_branch").asString("main"),
                review,
                reviewer);
        var kind = new ItemKind(issue ? "scm.issue" : review == null ? "scm.pull_request" : "scm.pull_request_review");
        var item = new ItemRef(workspaceId, source, kind, source, review == null ? null : source + 1, null);
        var version = new ItemVersion(fixture.path("itemVersion").asString("fixture"), null);
        var integration = new IntegrationRef(IntegrationKind.GITHUB, workspaceId, null);
        var snapshot = new ItemSnapshot(
                item,
                version,
                integration,
                Map.of(),
                List.of(),
                new ItemLabel(item.itemId(), "Work", "Pull request", 1, "owner/repo", null, IntegrationKind.GITHUB));
        var request = new CaptureRequest(UUID.randomUUID(), UUID.randomUUID(), 1, Set.of(), "2.0.0", 3);
        return new ScmCaptureContext(snapshot, request, work, new ScmCapturePreparation(), Instant.now());
    }

    public static ScmCaptureContext withVersion(ScmCaptureContext context, ItemVersion version) {
        var previous = context.pinned();
        return new ScmCaptureContext(
                new ItemSnapshot(
                        previous.item(),
                        version,
                        previous.integration(),
                        previous.state(),
                        previous.roles(),
                        previous.label()),
                context.request(),
                context.work(),
                context.preparation(),
                context.capturedAt());
    }
}
