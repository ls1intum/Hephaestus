package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class WorkArtifactTest extends BaseUnitTest {

    @Test
    void onlyThePullRequestCarriesAnInlineLane() {
        // The delivery pipeline branches on this capability (diff-note partition, inline placements,
        // conversational routing) — a new artifact type must consciously declare its lane.
        assertThat(WorkArtifact.PULL_REQUEST.hasInlineLane()).isTrue();
        assertThat(WorkArtifact.ISSUE.hasInlineLane()).isFalse();
        assertThat(WorkArtifact.CONVERSATION_THREAD.hasInlineLane()).isFalse();
    }
}
