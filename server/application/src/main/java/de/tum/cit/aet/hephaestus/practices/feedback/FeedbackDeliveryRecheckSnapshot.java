package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;

/** The send that a delivery recheck queues again. It names no words, people or provider references. */
record FeedbackDeliveryRecheckSnapshot(
        FeedbackSendAction action, FeedbackSendState state, int attempts, int unknownCopies, boolean withdrawn)
        implements ConfigAuditSnapshot {}
