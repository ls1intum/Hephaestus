package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins the {@link MetaJson} null-tolerant accessor contracts — in particular the "wrong type → null"
 * guards (optString gates on isString(), optLong on isNumber()). Without these asserts a regression that
 * dropped a type guard would let a numeric node coerce to a digit string and masquerade as a branch/SHA,
 * yet every provider test only ever feeds String literals, so the guard branch would be untested.
 */
class MetaJsonTest extends BaseUnitTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode node() {
        ObjectNode n = mapper.createObjectNode();
        n.put("text", "feature/auth-fix");
        n.put("blank", "  ");
        n.put("number", 42);
        n.put("flag", true);
        n.putNull("nulled");
        return n;
    }

    @Test
    void optString_returnsValueForTextNode() {
        assertThat(MetaJson.optString(node(), "text")).isEqualTo("feature/auth-fix");
    }

    @Test
    void optString_returnsNullForWrongTypeOrBlankOrAbsent() {
        ObjectNode n = node();
        assertThat(MetaJson.optString(n, "number")).isNull(); // numeric must NOT coerce to "42"
        assertThat(MetaJson.optString(n, "flag")).isNull(); // boolean must NOT coerce to "true"
        assertThat(MetaJson.optString(n, "blank")).isNull();
        assertThat(MetaJson.optString(n, "nulled")).isNull();
        assertThat(MetaJson.optString(n, "missing")).isNull();
    }

    @Test
    void optLong_returnsValueForNumberAndNullOtherwise() {
        ObjectNode n = node();
        assertThat(MetaJson.optLong(n, "number")).isEqualTo(42L);
        assertThat(MetaJson.optLong(n, "text")).isNull(); // string must NOT parse to a long
        assertThat(MetaJson.optLong(n, "nulled")).isNull();
        assertThat(MetaJson.optLong(n, "missing")).isNull();
    }
}
