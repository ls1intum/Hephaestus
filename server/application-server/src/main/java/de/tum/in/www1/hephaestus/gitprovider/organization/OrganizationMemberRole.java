package de.tum.in.www1.hephaestus.gitprovider.organization;

/**
 * The role of a user in an organization (provider-agnostic).
 * <p>
 * GitHub: maps directly to OrganizationMemberRole (ADMIN, MEMBER).
 * GitLab: OWNER/MAINTAINER → ADMIN, DEVELOPER/REPORTER/GUEST → MEMBER.
 */
public enum OrganizationMemberRole {
    /** The user is an administrator of the organization. */
    ADMIN,

    /** The user is a member of the organization. */
    MEMBER,
}
