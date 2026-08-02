package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeDefinitionValidatorTest extends BaseUnitTest {

    @Test
    void rejectsDuplicateTriggerEvents() {
        assertThatThrownBy(() ->
            PracticeDefinitionValidator.validate(
                WorkArtifact.PULL_REQUEST,
                List.of("PullRequestCreated", "PullRequestCreated"),
                null,
                null
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Trigger events must not contain duplicates");
    }
}
