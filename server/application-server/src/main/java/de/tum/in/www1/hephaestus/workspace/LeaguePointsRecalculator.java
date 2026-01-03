package de.tum.in.www1.hephaestus.workspace;

/**
 * Service Provider Interface (SPI) for league points recalculation.
 *
 * <p>This interface allows the workspace module to trigger league point recalculation
 * without depending on the leaderboard module's implementation details.
 * The leaderboard module provides the implementation of this interface.
 */
public interface LeaguePointsRecalculator {
    /**
     * Recalculates league points for all members of the given workspace.
     *
     * <p>This method iterates through historical contribution windows and updates
     * each member's league points based on their leaderboard performance.
     *
     * @param workspace the workspace for which to recalculate league points
     */
    void recalculate(Workspace workspace);
}
