package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.spi.CitationProof;
import de.tum.cit.aet.hephaestus.practices.spi.CitationVerificationStatus;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

@Tag("unit")
class PinnedInlineAnchorsTest {
    private final PinnedInlineAnchors anchors = new PinnedInlineAnchors(JsonNodeFactory.instance
            .objectNode()
            .put("base_sha", "base")
            .put("head_sha", "head")
            .put("diff", """
                    diff --git a/old.java b/new.java
                    similarity index 80%
                    rename from old.java
                    rename to new.java
                    --- a/old.java
                    +++ b/new.java
                    @@ -3,3 +3,3 @@
                     context
                    -unsafe();
                    +checked();
                     tail
                    """));

    private EvidenceCitation citation(String path, CitationSide side, String commit, int start, int end) {
        return new EvidenceCitation(
                new SourceKind("scm.repository.tree"),
                "repo/.git/HEAD",
                path,
                side,
                commit,
                start,
                end,
                "checked();",
                new CitationProof(CitationVerificationStatus.VERIFIED, "item", "quote"));
    }

    @Test
    void shouldPermitOnlyThePinnedSidePathRevisionAndHunkRange() {
        assertThat(anchors.permits(citation("new.java", CitationSide.NEW, "head", 4, 4)))
                .isTrue();
        assertThat(anchors.permits(citation("old.java", CitationSide.OLD, "base", 4, 4)))
                .isTrue();
        assertThat(anchors.permits(citation("old.java", CitationSide.NEW, "head", 4, 4)))
                .isFalse();
        assertThat(anchors.permits(citation("new.java", CitationSide.NEW, "historical", 4, 4)))
                .isFalse();
        assertThat(anchors.permits(citation("new.java", CitationSide.NEW, "head", 4, 6)))
                .isFalse();
        assertThat(anchors.permits(citation("new.java", CitationSide.NEW, "head", 20, 20)))
                .isFalse();
        assertThat(anchors.permits(citation("new.java", CitationSide.NEW, "head", 3, 3)))
                .isFalse();
        assertThat(anchors.permits(citation("untouched.java", CitationSide.NEW, "head", 4, 4)))
                .isFalse();
    }
}
