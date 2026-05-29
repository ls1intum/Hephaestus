package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.Commit;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;

/**
 * Object-mother for the owned JPA {@code @Entity} POJOs used across unit tests.
 *
 * <p><b>Why this exists.</b> These entities all carry {@code @NoArgsConstructor + @Setter}
 * (Lombok), so the real object is cheaper and strictly more faithful than a Mockito mock.
 * Mocking an owned entity and stubbing its getters is tautological —
 * {@code when(ws.getId()).thenReturn(1L)} followed by an assertion on {@code 1L} tests the
 * stub, not the system under test, and couples the test to getter names instead of real
 * entity wiring (relationships, equals/hashCode). Constructing the real object exercises the
 * same field plumbing the production code relies on.
 *
 * <p>Each factory sets only the minimal identifying fields. Tests that need more should set
 * additional fields on the returned instance directly — the entity is fully mutable.
 *
 * <p><b>Guarded by</b> {@code NoMockingOwnedEntitiesTest}: re-introducing
 * {@code mock(<Entity>.class)} for any of these owned entities fails the architecture suite.
 */
public final class TestEntities {

    private TestEntities() {}

    /**
     * A {@link Repository} with id and {@code nameWithOwner} set.
     *
     * <p>Null arguments are skipped (the field's Lombok {@code @NonNull} setter rejects null);
     * leaving a field unset yields its natural default, which is what tests exercising the
     * "missing value" path want.
     */
    public static Repository repository(Long id, String nameWithOwner) {
        Repository repo = new Repository();
        repo.setId(id);
        if (nameWithOwner != null) {
            repo.setNameWithOwner(nameWithOwner);
            if (nameWithOwner.contains("/")) {
                repo.setName(nameWithOwner.substring(nameWithOwner.indexOf('/') + 1));
            }
        }
        return repo;
    }

    /** A {@link Repository} with id, {@code nameWithOwner} and {@code defaultBranch} set. */
    public static Repository repository(Long id, String nameWithOwner, String defaultBranch) {
        Repository repo = repository(id, nameWithOwner);
        if (defaultBranch != null) {
            repo.setDefaultBranch(defaultBranch);
        }
        return repo;
    }

    /** An {@link Organization} with id and {@code login} set. */
    public static Organization organization(Long id, String login) {
        Organization org = new Organization();
        org.setId(id);
        org.setLogin(login);
        return org;
    }

    /** A {@link Commit} with id and {@code sha} set. */
    public static Commit commit(Long id, String sha) {
        Commit commit = new Commit();
        commit.setId(id);
        commit.setSha(sha);
        return commit;
    }

    /** A {@link PullRequest} with id, {@code number} and {@code title} set. */
    public static PullRequest pullRequest(Long id, int number, String title) {
        PullRequest pr = new PullRequest();
        pr.setId(id);
        pr.setNumber(number);
        pr.setTitle(title);
        return pr;
    }

    /** A {@link Workspace} with id set. */
    public static Workspace workspace(Long id) {
        Workspace ws = new Workspace();
        ws.setId(id);
        return ws;
    }

    /** A {@link Workspace} with id and {@code workspaceSlug} set. */
    public static Workspace workspace(Long id, String slug) {
        Workspace ws = workspace(id);
        ws.setWorkspaceSlug(slug);
        return ws;
    }

    /** A {@link GitProvider} with id and {@code type} set. */
    public static GitProvider gitProvider(Long id, GitProviderType type) {
        GitProvider provider = new GitProvider();
        provider.setId(id);
        provider.setType(type);
        return provider;
    }

    /** An {@link AgentJob} with no fields set (mutate as needed). */
    public static AgentJob agentJob() {
        return new AgentJob();
    }
}
