package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedbackBasisInput(UUID resultId, @Nullable UUID problemId, int ordinal) {}
