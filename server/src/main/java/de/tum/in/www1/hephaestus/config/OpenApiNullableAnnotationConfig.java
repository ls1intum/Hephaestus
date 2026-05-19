package de.tum.in.www1.hephaestus.config;

import io.swagger.v3.core.jackson.ModelResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;

// Jackson is configured with NON_NULL inclusion: null property values never appear on the wire.
// swagger-core ≥2.2.45 auto-detects any annotation named @Nullable and emits OpenAPI 3.1
// `type: [x, "null"]`, which lies about the contract (3 states emitted, 2 states real).
// Clearing this list keeps @Nullable as a static-analysis hint without leaking into the schema.
// Explicit @Schema(nullable = true) still works (checked before the annotation scan).
@Configuration
class OpenApiNullableAnnotationConfig {

    static {
        ModelResolver.NULLABLE_ANNOTATIONS = List.of();
    }
}
