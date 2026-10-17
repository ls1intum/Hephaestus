package de.tum.cit.aet.hephaestus.practices.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CatalogInputDTOTest extends BaseUnitTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldKeepGroupCreateFieldsFlatWhenBindingSharedUpdateContent() {
        var json = mapper.readTree("""
            {"slug":"quality","name":"Quality","description":null,"icon":null,"color":null,"position":null}
            """);
        var input = mapper.treeToValue(json, CatalogGroupInputDTO.class);
        assertThat(input.slug()).isEqualTo("quality");
        assertThat(input.content()).isEqualTo(new CatalogGroupUpdateDTO("Quality", null, null, null, null));
        JsonNode serialized = mapper.valueToTree(input);
        assertThat(serialized).isEqualTo(json);
    }

    @Test
    void shouldKeepPracticeCreateFieldsFlatWhenBindingSharedUpdateContent() {
        var update = new CatalogPracticeUpdateDTO(
                "scm.pull_request", null, DefinitionTestFixtures.authoring(DefinitionTestFixtures.definition()), null);
        var json = (ObjectNode) mapper.valueToTree(update);
        json.put("slug", "quality");
        var input = mapper.treeToValue(json, CatalogPracticeInputDTO.class);
        assertThat(input.slug()).isEqualTo("quality");
        assertThat(input.content()).isEqualTo(update);
        JsonNode serialized = mapper.valueToTree(input);
        assertThat(serialized).isEqualTo(json);
        json.put("unexpected", true);
        assertThatThrownBy(() -> mapper.treeToValue(json, CatalogPracticeInputDTO.class))
                .isInstanceOf(JacksonException.class);
    }
}
