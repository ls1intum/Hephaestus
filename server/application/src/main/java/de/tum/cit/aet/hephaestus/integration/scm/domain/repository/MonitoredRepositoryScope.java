package de.tum.cit.aet.hephaestus.integration.scm.domain.repository;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Whether a workspace's {@code repository_to_monitor} row covers a {@code repository} row of the same
 * {@code name_with_owner}.
 *
 * <p>Repositories are unique per {@code (provider_id, name_with_owner)}, but a monitor stores only the name. A name
 * match alone lets a GitLab project count as a GitHub repository another workspace monitors under the same name, and
 * the other way round. So a monitor covers a repository only when, besides the name:
 * <ul>
 *   <li>the monitor's native id, once known, is the repository's, and
 *   <li>the repository's provider has the kind of the workspace's SCM connection. A workspace without an SCM
 *       connection has no provider to compare, so its name match stands.
 * </ul>
 * No provider id is compared. Two servers of the same kind (two GitLab instances) are told apart only by the native
 * id, which the sync fills in. Until it does, a monitor also covers a repository of its name on the other server of
 * that kind.
 *
 * <p>JPQL calls {@value #FUNCTION}{@code (monitor.workspace.id, monitor.nativeId, repository.nativeId,
 * repository.provider.id) = true}; native SQL appends {@link #NATIVE_RTM_R}. Keep the join's name match: it is what
 * the indexes serve.
 */
public final class MonitoredRepositoryScope implements FunctionContributor {

    /** The JPQL function this class contributes to Hibernate. */
    public static final String FUNCTION = "monitor_covers_repository";

    /**
     * The predicate over {@code ?1} monitor workspace id, {@code ?2} monitor native id, {@code ?3} repository native
     * id and {@code ?4} repository provider id.
     */
    static final String PATTERN = "((?2 IS NULL OR ?2 = ?3)"
            + " AND (NOT EXISTS (SELECT 1 FROM connection monitor_connection"
            + " WHERE monitor_connection.workspace_id = ?1 AND monitor_connection.kind IN ('GITHUB', 'GITLAB'))"
            + " OR EXISTS (SELECT 1 FROM connection monitor_connection"
            + " JOIN identity_provider monitor_provider ON monitor_provider.type = monitor_connection.kind"
            + " WHERE monitor_connection.workspace_id = ?1 AND monitor_provider.id = ?4)))";

    /**
     * {@link #PATTERN} for native SQL that aliases {@code repository_to_monitor} as {@code rtm} and {@code repository}
     * as {@code r}. A unit test keeps it equal to the pattern.
     */
    public static final String NATIVE_RTM_R = "((rtm.native_id IS NULL OR rtm.native_id = r.native_id)"
            + " AND (NOT EXISTS (SELECT 1 FROM connection monitor_connection"
            + " WHERE monitor_connection.workspace_id = rtm.workspace_id"
            + " AND monitor_connection.kind IN ('GITHUB', 'GITLAB'))"
            + " OR EXISTS (SELECT 1 FROM connection monitor_connection"
            + " JOIN identity_provider monitor_provider ON monitor_provider.type = monitor_connection.kind"
            + " WHERE monitor_connection.workspace_id = rtm.workspace_id AND monitor_provider.id = r.provider_id)))";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions
                .getFunctionRegistry()
                .registerPattern(
                        FUNCTION,
                        PATTERN,
                        contributions
                                .getTypeConfiguration()
                                .getBasicTypeRegistry()
                                .resolve(StandardBasicTypes.BOOLEAN));
    }
}
