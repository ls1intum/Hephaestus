package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository.AdminVisibleResponseRow;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AdminVisibleResponseRowTest {

    @Test
    void shouldCarryNoRatingAndNoPrivateNoteWhenAnAdminReadsAResponse() {
        assertThat(Arrays.stream(AdminVisibleResponseRow.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("getFeedbackId", "getRespondedAt", "getDisputed", "getDisputeExplanation")
                .doesNotContain("getUsefulness", "getResolution", "getComment");
    }
}
