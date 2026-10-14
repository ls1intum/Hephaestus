package de.tum.cit.aet.hephaestus.core.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.jspecify.annotations.Nullable;

// Audit snapshots must preserve null fields, ISO timestamps, and field order independently of application JSON
// settings.
final class ConfigAuditSnapshotMapper {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .defaultPropertyInclusion(
                    JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ConfigAuditSnapshotMapper() {}

    @Nullable
    static JsonNode toNode(@Nullable ConfigAuditSnapshot snapshot) {
        return snapshot == null ? null : MAPPER.valueToTree(snapshot);
    }

    @Nullable
    static String toJson(@Nullable JsonNode node) {
        return node == null ? null : node.toString();
    }
}
