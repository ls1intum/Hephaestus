package de.tum.in.www1.hephaestus.gitprovider.organization;

/**
 * The role of a user in an organization.
 * <p>
 * Maps to GitHub's OrganizationMemberRole enum values.
 */
public enum OrganizationMemberRole {
    /** The user is an administrator of the organization. */
    ADMIN,

    /** The user is a member of the organization. */
    MEMBER,
}
