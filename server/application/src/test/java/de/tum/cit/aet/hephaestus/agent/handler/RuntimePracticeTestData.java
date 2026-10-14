package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeGuidance;
import de.tum.cit.aet.hephaestus.practices.PracticeReadSource;
import de.tum.cit.aet.hephaestus.practices.PracticeReads;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.ReviewedBy;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Complete definitions for persistence fixtures; all writes use the owning version service. */
public final class RuntimePracticeTestData {
    private RuntimePracticeTestData() {}

    public static PracticeDefinition definition(ItemKind kind, String name, String criteria, @Nullable String script) {
        String event =
                switch (kind.value()) {
                    case "docs.document" -> "published";
                    case "chat.thread" -> "settled";
                    default -> "opened";
                };
        String source =
                switch (kind.value()) {
                    case "scm.pull_request" -> "scm.pull-request.core";
                    case "scm.issue" -> "scm.issue.core";
                    case "chat.thread" -> "slack.conversation.thread";
                    case "docs.document" -> "docs.document.core";
                    default -> throw new IllegalArgumentException("Unsupported fixture kind");
                };
        return new PracticeDefinition(
                name,
                new ReviewOn(List.of(new ItemEventName(kind.value() + "." + event)), Map.of()),
                null,
                AboutRole.AUTHOR,
                new PracticeReads("2.0.0", List.of(new PracticeReadSource(new SourceKind(source), ReadNeed.REQUIRED))),
                new ReviewedBy(ReviewedByKind.HEPHAESTUS, null, List.of()),
                criteria,
                script,
                null,
                kind.value().equals("scm.pull_request"),
                new PracticeGuidance(null, null, null, null, null));
    }
}
