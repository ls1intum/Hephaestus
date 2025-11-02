package de.tum.in.www1.hephaestus.gitprovider.issue;

/**
 * Represents the association between an issue/PR author and the repository.
 * Matches GitHub's author_association field.
 */
public enum AuthorAssociation {
    /** Author owns the repository. */
    OWNER,
    /** Author is a member of the organization that owns the repository. */
    MEMBER,
    /** Author has been invited to collaborate on the repository. */
    COLLABORATOR,
    /** Author has previously committed to the repository. */
    CONTRIBUTOR,
    /** Author has not previously committed to the repository. */
    FIRST_TIME_CONTRIBUTOR,
    /** Author has previously commented on the repository. */
    FIRST_TIMER,
    /** Author has no association with the repository. */
    NONE,
    /** Author is a mannequin (placeholder user for imported issues). */
    MANNEQUIN,
}
