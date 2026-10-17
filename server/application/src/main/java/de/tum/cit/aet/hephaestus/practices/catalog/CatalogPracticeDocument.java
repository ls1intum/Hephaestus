package de.tum.cit.aet.hephaestus.practices.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogPracticeDocument(
        ItemKind kind, @Nullable String groupSlug, PracticeDefinition definition) {
    public CatalogPracticeDocument {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(definition);
    }

    public Content content() {
        return new Content(groupSlug, definition);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Content(@Nullable String groupSlug, PracticeDefinition definition) implements ClosedPracticeInput {}
}
