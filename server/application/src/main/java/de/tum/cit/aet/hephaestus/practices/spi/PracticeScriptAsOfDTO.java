package de.tum.cit.aet.hephaestus.practices.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "The review whose practice script run reported a practice's needs")
public record PracticeScriptAsOfDTO(
        @NonNull UUID jobId, @NonNull Instant finishedAt) {}
