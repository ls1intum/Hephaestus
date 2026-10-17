package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;

/**
 * What each practice's script needs, without exposing agent-job persistence or model routing
 * to the practices module. A script declares its needs only when it runs, so they come from the newest
 * review that ran it.
 */
public interface PracticeScriptSummaries {
    /**
     * @return one summary per practice of the workspace that has a practice script, by practice name
     */
    List<PracticeScriptSummaryDTO> latestCurrent(long workspaceId);
}
