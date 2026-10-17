package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PracticeContainerValidationTest extends BaseUnitTest {
    @Test
    void shouldRejectUnknownNestedReadFieldsWhenAuthored() {
        var mapper = JsonMapper.builder().build();
        var raw = mapper.valueToTree(DefinitionTestFixtures.definition());
        if (!(raw.path("reads") instanceof ObjectNode reads)) throw new IllegalStateException();
        reads.put("credentials", "forbidden");
        assertThatThrownBy(() -> mapper.treeToValue(raw, PracticeDefinition.class))
                .isInstanceOf(RuntimeException.class);
    }
}
