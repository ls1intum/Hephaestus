package de.tum.cit.aet.hephaestus.core.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** OpenAPI-only schema for Spring's RFC 7807 / RFC 9457 problem responses. */
@Schema(name = "ProblemDetail", description = "RFC 7807 / RFC 9457 problem detail response")
public record ProblemDetailSchema(
    @Schema(description = "Problem type URI", example = "about:blank") URI type,
    @Schema(description = "Short, human-readable summary", example = "Forbidden") String title,
    @Schema(description = "HTTP status code", example = "403") Integer status,
    @Schema(description = "Human-readable detail", example = "Workspace ADMIN or OWNER is required") String detail,
    @Schema(description = "Request instance URI", example = "/workspaces/demo/practices/reports") URI instance,
    @Nullable
    @Schema(description = "Validation errors keyed by field name, when present")
    Map<String, List<String>> errors
) {}
