package de.tum.in.www1.hephaestus.gitprovider.common.spi;

/**
 * Listener for organization membership change events.
 * <p>
 * gitprovider defines this interface and calls it when organization membership changes.
 * The consuming module implements this to sync scope members accordingly.
 * <p>
 * This follows the Dependency Inversion Principle: gitprovider depends on the abstraction
 * it defines, not on consuming module concepts.
 */
public interface OrganizationMembershipListener {
    /**
     * Called when a member is added to an organization.
     *
     * @param event the membership added event data
     */
    void onMemberAdded(MembershipChangedEvent event);

    /**
     * Called when a member is removed from an organization.
     *
     * @param event the membership removed event data
     */
    void onMemberRemoved(MembershipChangedEvent event);

    /**
     * Called when organization membership data has been fully synced via scheduled sync.
     * <p>
     * Implementations should reconcile scope members with the synced organization data.
     * This is different from add/remove events which handle incremental webhook changes.
     *
     * @param event the organization sync completed event data
     */
    default void onOrganizationMembershipsSynced(OrganizationSyncedEvent event) {}

    /**
     * Event data for organization sync completion.
     *
     * @param organizationId    the GitHub organization ID
     * @param organizationLogin the GitHub organization login
     */
    record OrganizationSyncedEvent(Long organizationId, String organizationLogin) {}

    /**
     * Event data for organization membership changes.
     *
     * @param organizationId    the GitHub organization ID
     * @param organizationLogin the GitHub organization login
     * @param userId            the GitHub user ID
     * @param userLogin         the GitHub user login
     * @param role              the membership role (e.g., "admin", "member"), null for removals
     */
    record MembershipChangedEvent(
        Long organizationId,
        String organizationLogin,
        Long userId,
        String userLogin,
        String role
    ) {}
}
