package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;

/**
 * SCM-shaped feedback delivery marker. Implementations live in vendor packages
 * (e.g. {@code integration/github/feedback/GithubFeedbackChannel}). Splitting
 * the marker into a family-typed interface lets the agent layer compile-time-
 * dispatch via {@code FeedbackChannelRegistry.scm(kind)}.
 */
public interface ScmFeedbackChannel extends FeedbackChannel {
}
