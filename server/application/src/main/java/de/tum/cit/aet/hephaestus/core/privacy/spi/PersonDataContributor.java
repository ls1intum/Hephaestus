package de.tum.cit.aet.hephaestus.core.privacy.spi;

import java.util.List;
import org.springframework.core.Ordered;
import tools.jackson.databind.JsonNode;

/**
 * Each store must implement all three operations. Selection contains stable row keys, not content.
 * Export and erasure use those keys and the original frozen scope for projection or redaction values.
 * Neither operation may resolve the subject again or select additional rows.
 * Contributors must be idempotent. Database writes and the step receipt commit together.
 */
public interface PersonDataContributor extends Ordered {
    String store();

    PersonDataSelection select(PersonScope person);

    /** Export the subject's data; affected proof about others contributes only cleanup ids and counts. */
    List<JsonNode> export(PersonDataSelection selection, PersonScope frozenScope);

    long erase(PersonDataSelection selection, PersonScope frozenScope);

    /** Stop admitted writers before any store is erased. Repeat calls must be safe. */
    default void prepareErasure(PersonDataSelection selection) {}

    default List<ExternalDelivery> externalDeliveries(PersonDataSelection selection) {
        return List.of();
    }

    @Override
    default int getOrder() {
        return 0;
    }

    record ExternalDelivery(long workspaceId, String locator) {}
}
