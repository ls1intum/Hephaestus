package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackContentDigestIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    FeedbackContentDigest digests;

    @Test
    void shouldUseTheOneDatabaseDigestAndValidateWorkspaceOwnership() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.READY, null);
        String stored = jdbc.queryForObject("SELECT feedback_content_digest(?)", String.class, parent.getId());
        assertThat(digests.current(workspace.getId(), parent.getId())).isEqualTo(stored);
        assertThatThrownBy(() -> digests.current(workspace.getId() + 1, parent.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void shouldDistinguishNullBodyFromItsLiteralTextWithoutAnotherHashImplementation() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        jdbc.update("UPDATE feedback SET body=NULL WHERE id=? AND workspace_id=?", parent.getId(), workspace.getId());
        var missing = digests.current(workspace.getId(), parent.getId());
        jdbc.update("UPDATE feedback SET body='null' WHERE id=? AND workspace_id=?", parent.getId(), workspace.getId());
        assertThat(digests.current(workspace.getId(), parent.getId())).isNotEqualTo(missing);
    }
}
