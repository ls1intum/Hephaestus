package de.tum.cit.aet.hephaestus.workspace.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One account-wide answer; model names belong only to workspace-specific views. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccountAiChoiceDTO(
        @Nullable AiChoice choice, @Nullable Instant updatedAt) {}
