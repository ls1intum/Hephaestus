package de.tum.cit.aet.hephaestus.practices.review.feedbackmode;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeSource;

public record EffectiveFeedbackMode(FeedbackMode mode, FeedbackModeSource source) {}
