package de.tum.in.www1.hephaestus.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests verifying that secrets are encrypted at rest in the database.
 *
 * <p>These tests verify:</p>
 * <ul>
 *   <li>Secrets are stored encrypted in the database column</li>
 *   <li>Secrets are decrypted transparently when read via JPA</li>
 *   <li>Encryption is applied to all sensitive Workspace fields</li>
 * </ul>
 */
@DisplayName("Workspace secret encryption integration tests")
class WorkspaceSecretEncryptionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AesGcmEncryptor encryptor;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should store personal access token encrypted in database")
    void personalAccessTokenEncryptedInDatabase() {
        // Arrange
        String plainToken = "ghp_supersecrettoken123456789";
        Workspace workspace = createWorkspaceWithSecrets(plainToken, null, null);

        // Act
        Workspace saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();

        // Assert - verify raw database value is encrypted
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT personal_access_token FROM workspace WHERE id = ?",
            String.class,
            saved.getId()
        );

        assertThat(rawDbValue)
            .as("Token should be encrypted in database")
            .startsWith("ENC:v1:")
            .isNotEqualTo(plainToken);

        // Assert - verify JPA reads back the decrypted value
        Workspace loaded = workspaceRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getPersonalAccessToken())
            .as("Token should be decrypted when loaded via JPA")
            .isEqualTo(plainToken);
    }

    @Test
    @DisplayName("should store Slack token encrypted in database")
    void slackTokenEncryptedInDatabase() {
        // Arrange
        String plainToken = "xoxb-slack-bot-token-secret";
        Workspace workspace = createWorkspaceWithSecrets(null, plainToken, null);

        // Act
        Workspace saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();

        // Assert - verify raw database value is encrypted
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT slack_token FROM workspace WHERE id = ?",
            String.class,
            saved.getId()
        );

        assertThat(rawDbValue)
            .as("Slack token should be encrypted in database")
            .startsWith("ENC:v1:")
            .isNotEqualTo(plainToken);

        // Assert - verify JPA reads back the decrypted value
        Workspace loaded = workspaceRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSlackToken())
            .as("Slack token should be decrypted when loaded via JPA")
            .isEqualTo(plainToken);
    }

    @Test
    @DisplayName("should store Slack signing secret encrypted in database")
    void slackSigningSecretEncryptedInDatabase() {
        // Arrange
        String plainSecret = "slack-signing-secret-abc123";
        Workspace workspace = createWorkspaceWithSecrets(null, null, plainSecret);

        // Act
        Workspace saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();

        // Assert - verify raw database value is encrypted
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT slack_signing_secret FROM workspace WHERE id = ?",
            String.class,
            saved.getId()
        );

        assertThat(rawDbValue)
            .as("Slack signing secret should be encrypted in database")
            .startsWith("ENC:v1:")
            .isNotEqualTo(plainSecret);

        // Assert - verify JPA reads back the decrypted value
        Workspace loaded = workspaceRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSlackSigningSecret())
            .as("Slack signing secret should be decrypted when loaded via JPA")
            .isEqualTo(plainSecret);
    }

    @Test
    @DisplayName("should handle null secrets gracefully")
    void nullSecretsHandledGracefully() {
        // Arrange
        Workspace workspace = createWorkspaceWithSecrets(null, null, null);

        // Act
        Workspace saved = workspaceRepository.save(workspace);
        workspaceRepository.flush();

        // Assert
        Workspace loaded = workspaceRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getPersonalAccessToken()).isNull();
        assertThat(loaded.getSlackToken()).isNull();
        assertThat(loaded.getSlackSigningSecret()).isNull();
    }

    @Test
    @DisplayName("should produce different ciphertext for same plaintext (unique IV)")
    void uniqueIvPerEncryption() {
        // Arrange
        String sameToken = "identical-token-for-both";
        Workspace workspace1 = createWorkspaceWithSecrets(sameToken, null, null);
        workspace1.setWorkspaceSlug("workspace-one");

        Workspace workspace2 = createWorkspaceWithSecrets(sameToken, null, null);
        workspace2.setWorkspaceSlug("workspace-two");

        // Act
        workspaceRepository.save(workspace1);
        workspaceRepository.save(workspace2);
        workspaceRepository.flush();

        // Assert - same plaintext should produce different ciphertext (semantic security)
        String rawDb1 = jdbcTemplate.queryForObject(
            "SELECT personal_access_token FROM workspace WHERE slug = ?",
            String.class,
            "workspace-one"
        );
        String rawDb2 = jdbcTemplate.queryForObject(
            "SELECT personal_access_token FROM workspace WHERE slug = ?",
            String.class,
            "workspace-two"
        );

        assertThat(rawDb1).as("Same plaintext should produce different ciphertext (unique IV)").isNotEqualTo(rawDb2);
    }

    private Workspace createWorkspaceWithSecrets(String pat, String slackToken, String slackSigningSecret) {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("test-workspace-" + System.nanoTime());
        workspace.setDisplayName("Test Workspace");
        workspace.setAccountLogin("test-org");
        workspace.setAccountType(AccountType.ORG);
        workspace.setIsPubliclyViewable(false);
        workspace.setPersonalAccessToken(pat);
        workspace.setSlackToken(slackToken);
        workspace.setSlackSigningSecret(slackSigningSecret);
        return workspace;
    }
}
