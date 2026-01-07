package de.tum.in.www1.hephaestus.gitprovider.issue;

/**
 * The association of an author with the repository.
 * Used for issues, pull requests, and comments to indicate
 * the author's relationship to the repository.
 */
public enum AuthorAssociation {
    /** Author is a collaborator with direct access. */
    COLLABORATOR,

    /** Author has previously committed to the repository. */
    CONTRIBUTOR,

    /** Author has not previously committed (first-ever contribution). */
    FIRST_TIMER,

    /** Author's first contribution to this specific repository. */
    FIRST_TIME_CONTRIBUTOR,

    /** Author is a mannequin (placeholder for imported users). */
    MANNEQUIN,

    /** Author is a member of the organization. */
    MEMBER,

    /** Author has no association with the repository. */
    NONE,

    /** Author is the owner of the repository. */
    OWNER,
}
