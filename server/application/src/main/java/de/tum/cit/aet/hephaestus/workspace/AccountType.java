package de.tum.cit.aet.hephaestus.workspace;

/**
 * Type of git provider account associated with a workspace.
 */
public enum AccountType {
    /**
     * Organization account (GitHub organization, GitLab group)
     */
    ORG,

    /**
     * User account (GitHub user, GitLab personal namespace)
     */
    USER,
}
