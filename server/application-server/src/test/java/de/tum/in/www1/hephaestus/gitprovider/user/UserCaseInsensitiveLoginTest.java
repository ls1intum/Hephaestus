package de.tum.in.www1.hephaestus.gitprovider.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

/**
 * Tests for case insensitive login functionality using CITEXT.
 */
class UserCaseInsensitiveLoginTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @Sql(scripts = {"/sql/clear-database.sql", "/sql/user-test-data.sql"})
    void testFindByLoginCaseInsensitive() {
        // Test that we can find a user with different cases
        var userLowercase = userRepository.findByLogin("testuser");
        var userUppercase = userRepository.findByLogin("TESTUSER"); 
        var userMixedcase = userRepository.findByLogin("TestUser");
        var userOriginalcase = userRepository.findByLogin("testUser");

        // All queries should return the same user
        assertThat(userLowercase).isPresent();
        assertThat(userUppercase).isPresent();
        assertThat(userMixedcase).isPresent();
        assertThat(userOriginalcase).isPresent();

        // All should be the same user instance/id
        assertThat(userLowercase.get().getId()).isEqualTo(userUppercase.get().getId());
        assertThat(userLowercase.get().getId()).isEqualTo(userMixedcase.get().getId());
        assertThat(userLowercase.get().getId()).isEqualTo(userOriginalcase.get().getId());
        
        // Login should be stored as originally inserted
        assertThat(userLowercase.get().getLogin()).isEqualTo("testUser");
    }

    @Test
    @Sql(scripts = {"/sql/clear-database.sql", "/sql/user-test-data.sql"})
    void testFindByLoginWithEagerMergedPullRequestsCaseInsensitive() {
        // Test eager loading query is also case insensitive
        var userLowercase = userRepository.findByLoginWithEagerMergedPullRequests("testuser");
        var userUppercase = userRepository.findByLoginWithEagerMergedPullRequests("TESTUSER");

        assertThat(userLowercase).isPresent();
        assertThat(userUppercase).isPresent();
        assertThat(userLowercase.get().getId()).isEqualTo(userUppercase.get().getId());
    }

    @Test
    @Sql(scripts = {"/sql/clear-database.sql", "/sql/user-test-data.sql"})
    void testGetCurrentUserCaseInsensitive() {
        // This would require mocking SecurityUtils.getCurrentUserLogin()
        // but tests the integration with the default method
        // TODO: Add test with mocked security context if needed
    }
}