package de.tum.cit.aet.hephaestus.integration.outline.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Response of Outline's {@code documents.export}: the document body rendered as Markdown in {@code data}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutlineExportResponse(@Nullable String data) {}
