package de.tum.in.www1.hephaestus.account;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link UserPreferences} entities.
 * <p>
 * Manages platform preferences that are separate from the git provider User entity.
 *
 * @see UserPreferences
 * @see AccountService
 */
@WorkspaceAgnostic("User preferences are user-scoped, not workspace-specific")
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    /**
     * Find preferences by user ID.
     *
     * @param userId the ID of the user
     * @return the user's preferences, or empty if not yet created
     */
    Optional<UserPreferences> findByUserId(Long userId);

    /**
     * Find preferences by user login.
     *
     * @param login the login (username) of the user
     * @return the user's preferences, or empty if not yet created
     */
    Optional<UserPreferences> findByUserLogin(String login);
}
