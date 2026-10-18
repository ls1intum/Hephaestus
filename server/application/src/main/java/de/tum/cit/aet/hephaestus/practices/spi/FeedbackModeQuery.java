package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Collection;
import java.util.Map;

/** The feedback mode in force for each practice now, resolved practice, group, workspace, then instance. */
public interface FeedbackModeQuery {
    /** A practice outside the workspace has no entry; callers treat it as {@link FeedbackMode#OFF}. */
    Map<Long, FeedbackMode> effectiveModes(long workspaceId, Collection<Long> practiceIds);
}
