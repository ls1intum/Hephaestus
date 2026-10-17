package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeMarkdownValidationTest extends BaseUnitTest {
    @Test
    void shouldPreserveMultilineCriteriaWhenRoundTripped() {
        String markdown = "# Heading\n\n- First check\n- Second check\n";
        var definition = DefinitionTestFixtures.definition("Markdown practice", markdown);
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.treeToValue(mapper.valueToTree(definition), PracticeDefinition.class)
                        .criteria())
                .isEqualTo(markdown);
    }
}
