package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code collections.documents}: the collection's document tree. Each node carries the
 * document id, its title, its {@code url} (the last path segment is the document slug), and its nested
 * {@code children}. The nesting is the source of a document's parent — the sync flattens the tree to learn
 * collection membership and parent relationships.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineCollectionDocumentsResponse(@Nullable List<Node> data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(
        @Nullable String id,
        @Nullable String title,
        @Nullable String url,
        @Nullable List<Node> children
    ) {}
}
