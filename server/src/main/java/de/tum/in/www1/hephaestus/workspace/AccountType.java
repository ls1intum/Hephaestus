package de.tum.in.www1.hephaestus.workspace;

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
