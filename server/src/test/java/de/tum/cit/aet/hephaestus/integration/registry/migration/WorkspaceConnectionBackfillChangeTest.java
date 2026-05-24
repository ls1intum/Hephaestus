package de.tum.cit.aet.hephaestus.integration.registry.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.registry.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.lang.Nullable;

/**
 * Pins the {@link WorkspaceConnectionBackfillChange} per-mode INSERT shape, idempotency,
 * and the legacy → new credential rewrap.
 *
 * <p>The change runs inside Liquibase with a {@link JdbcConnection}, no Spring context.
 * These tests mock the JDBC surface and assert the exact INSERT parameters issued for
 * each {@code git_provider_mode} branch plus the Slack branch. Once any mode's SQL
 * shape drifts from what the new {@code Connection} JPA entity + converters expect,
 * the next migration would silently produce unloadable rows — catching that drift here
 * is much cheaper than catching it after the column drop.
 */
@DisplayName("WorkspaceConnectionBackfillChange — per-mode INSERT shape")
class WorkspaceConnectionBackfillChangeTest extends BaseUnitTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String SAVED_KEY_PROP = "hephaestus.security.encryption-key";

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Mock Database database;
    @Mock JdbcConnection jdbcConnection;
    @Mock Statement selectStatement;
    @Mock PreparedStatement insertStatement;

    /** Captured INSERT parameters, one entry per executeUpdate() call. */
    private final List<InsertCapture> capturedInserts = new ArrayList<>();
    /** Per-call accumulator the executeUpdate stub flushes into capturedInserts. */
    private final Map<Integer, Object> nextInsert = new HashMap<>();

    @Nullable private String previousKey;

    @BeforeEach
    void wireKeyAndJdbcSurface() throws Exception {
        previousKey = System.getProperty(SAVED_KEY_PROP);
        System.setProperty(SAVED_KEY_PROP, KEY);

        when(database.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.createStatement()).thenReturn(selectStatement);

        // Backfill no longer pre-checks existence — it relies on
        // INSERT ... ON CONFLICT DO NOTHING for atomic idempotency.
        lenient().when(jdbcConnection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("INSERT INTO connection")) return insertStatement;
            throw new AssertionError("Unexpected prepareStatement SQL: " + sql);
        });

        wireInsertCapture();
    }

    @AfterEach
    void restoreKey() {
        if (previousKey == null) {
            System.clearProperty(SAVED_KEY_PROP);
        } else {
            System.setProperty(SAVED_KEY_PROP, previousKey);
        }
    }

    @Nested
    @DisplayName("Per-mode INSERT shape")
    class PerMode {

        @Test
        @DisplayName("GITHUB_APP_INSTALLATION → kind=GITHUB, instance_key=installation_id, no credentials")
        void githubAppInstallation_insertsRowWithoutCredentials() throws Exception {
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(42L)
                .mode("GITHUB_APP_INSTALLATION")
                .installationId(987654L)
                .accountLogin("ls1intum")
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            InsertCapture capture = onlyInsert();
            assertThat(capture.workspaceId).isEqualTo(42L);
            assertThat(capture.kind).isEqualTo(IntegrationKind.GITHUB.name());
            assertThat(capture.instanceKey).isEqualTo("987654");
            assertThat(capture.state).isEqualTo(IntegrationState.ACTIVE.name());
            assertThat(capture.credentialsEncrypted).isNull();
            assertThat(capture.credentialsAlg).isNull();

            JsonNode config = MAPPER.readTree(capture.configJson);
            assertThat(config.get("type").asText()).isEqualTo("GITHUB_APP");
            assertThat(config.get("installationId").asLong()).isEqualTo(987654L);
            assertThat(config.get("orgLogin").asText()).isEqualTo("ls1intum");
            assertThat(config.get("enabledStreams")).isEmpty();
        }

        @Test
        @DisplayName("PAT_ORG → kind=GITHUB, instance_key='pat', credentials rewrapped as BearerToken")
        void patOrg_rewrapsTokenIntoBearerBundle() throws Exception {
            String plaintextPat = "ghp_pretend-this-is-a-real-token";
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(7L)
                .mode("PAT_ORG")
                .accountLogin("octocat")
                .serverUrl("https://github.example.com/api/v3")
                .personalAccessToken(legacyEncrypt(plaintextPat))
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            InsertCapture capture = onlyInsert();
            assertThat(capture.kind).isEqualTo(IntegrationKind.GITHUB.name());
            assertThat(capture.instanceKey).isEqualTo("pat");
            assertThat(capture.credentialsEncrypted).isNotNull();
            assertThat(capture.credentialsAlg).isEqualTo(CredentialBundleConverter.ALGORITHM_TAG);

            // Round-trip through the production converter to prove the bytes are loadable.
            CredentialBundleConverter readback = new CredentialBundleConverter(KEY, "dev");
            assertThat(readback.convertToEntityAttribute(capture.credentialsEncrypted))
                .isEqualTo(new BearerToken(plaintextPat, null));

            JsonNode config = MAPPER.readTree(capture.configJson);
            assertThat(config.get("type").asText()).isEqualTo("GITHUB_PAT");
            assertThat(config.get("orgLogin").asText()).isEqualTo("octocat");
            assertThat(config.get("serverUrl").asText()).isEqualTo("https://github.example.com/api/v3");
        }

        @Test
        @DisplayName("GITLAB_PAT → kind=GITLAB, instance_key='<serverUrl>:<groupId>', signingMode=PLAINTEXT")
        void gitlabPat_composesInstanceKeyFromUrlAndGroupId() throws Exception {
            String plaintextPat = "glpat-not-a-real-token-aaaa";
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(99L)
                .mode("GITLAB_PAT")
                .serverUrl("https://gitlab.lrz.de")
                .gitlabGroupId(252106L)
                .gitlabWebhookId(7L)
                .personalAccessToken(legacyEncrypt(plaintextPat))
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            InsertCapture capture = onlyInsert();
            assertThat(capture.kind).isEqualTo(IntegrationKind.GITLAB.name());
            assertThat(capture.instanceKey).isEqualTo("https://gitlab.lrz.de:252106");

            CredentialBundleConverter readback = new CredentialBundleConverter(KEY, "dev");
            assertThat(readback.convertToEntityAttribute(capture.credentialsEncrypted))
                .isEqualTo(new BearerToken(plaintextPat, null));

            JsonNode config = MAPPER.readTree(capture.configJson);
            assertThat(config.get("type").asText()).isEqualTo("GITLAB");
            assertThat(config.get("serverUrl").asText()).isEqualTo("https://gitlab.lrz.de");
            assertThat(config.get("gitlabGroupId").asLong()).isEqualTo(252106L);
            assertThat(config.get("gitlabWebhookId").asLong()).isEqualTo(7L);
            assertThat(config.get("signingMode").asText()).isEqualTo("PLAINTEXT");
        }

        @Test
        @DisplayName("Slack token present → SLACK connection inserted with sentinel instance_key and rewrapped bot token")
        void slackTokenPresent_insertsSlackConnection() throws Exception {
            String plaintextSlackToken = "xoxb-12345-pretend";
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(11L)
                .mode("GITHUB_APP_INSTALLATION")
                .installationId(1L)
                .accountLogin("any")
                .slackToken(legacyEncrypt(plaintextSlackToken))
                .leaderboardNotificationTeam("Engineering")
                .leaderboardNotificationChannelId("C012345")
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            // Two inserts: GITHUB (App) + SLACK. Inspect the SLACK one.
            assertThat(capturedInserts).hasSize(2);
            InsertCapture slack = capturedInserts.stream()
                .filter(c -> IntegrationKind.SLACK.name().equals(c.kind))
                .findFirst()
                .orElseThrow();

            assertThat(slack.instanceKey).isEqualTo("pending-team-bind");
            assertThat(slack.credentialsEncrypted).isNotNull();
            assertThat(slack.credentialsAlg).isEqualTo(CredentialBundleConverter.ALGORITHM_TAG);

            CredentialBundleConverter readback = new CredentialBundleConverter(KEY, "dev");
            assertThat(readback.convertToEntityAttribute(slack.credentialsEncrypted))
                .isEqualTo(new BearerToken(plaintextSlackToken, null));

            JsonNode config = MAPPER.readTree(slack.configJson);
            assertThat(config.get("type").asText()).isEqualTo("SLACK");
            assertThat(config.get("teamId").isNull()).isTrue();  // we don't know it without an API call
            assertThat(config.get("notificationChannelId").asText()).isEqualTo("C012345");
            assertThat(config.get("teamLabel").asText()).isEqualTo("Engineering");
        }
    }

    @Nested
    @DisplayName("Idempotency + edge cases")
    class Edges {

        @Test
        @DisplayName("INSERT carries ON CONFLICT DO NOTHING — concurrent applies cannot duplicate rows")
        void insertUsesOnConflictDoNothing() throws Exception {
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(1L)
                .mode("GITHUB_APP_INSTALLATION")
                .installationId(1L)
                .accountLogin("any")
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            verify(jdbcConnection).prepareStatement(sql.capture());
            assertThat(sql.getValue())
                .startsWith("INSERT INTO connection")
                .contains("ON CONFLICT (workspace_id, kind, instance_key) DO NOTHING");
        }

        @Test
        @DisplayName("Existing Connection (executeUpdate returns 0 from ON CONFLICT) → counted as skipped, no error")
        void existingConnection_executesUpdateButRecordsZeroRows() throws Exception {
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(1L)
                .mode("GITHUB_APP_INSTALLATION")
                .installationId(1L)
                .accountLogin("any")
                .build());
            // The unique constraint absorbs the INSERT: executeUpdate returns 0.
            when(insertStatement.executeUpdate()).thenReturn(0);

            new WorkspaceConnectionBackfillChange().execute(database);

            verify(insertStatement, times(1)).executeUpdate();
            // The capture stub records nothing because our wireInsertCapture treats
            // executeUpdate's return value as the row-count proxy — and 0 means no row
            // was actually inserted by the DB.
        }

        @Test
        @DisplayName("Slack token absent → no SLACK row inserted")
        void slackTokenAbsent_skipsSlackInsert() throws Exception {
            stubSingleWorkspace(WorkspaceRow.builder()
                .id(1L)
                .mode("GITHUB_APP_INSTALLATION")
                .installationId(1L)
                .accountLogin("any")
                .slackToken(null)
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            verify(insertStatement, times(1)).executeUpdate();
            assertThat(capturedInserts).singleElement().satisfies(c ->
                assertThat(c.kind).isEqualTo(IntegrationKind.GITHUB.name()));
        }

        @Test
        @DisplayName("PAT-mode with NULL token → row inserted with NULL credentials (no key required)")
        void patModeWithoutToken_insertsRowWithoutCredentials() throws Exception {
            // Remove the encryption key — App-mode and token-less PAT paths must NOT
            // need a key. (PAT-mode workspaces without a stored token exist in dev
            // where the user is mid-bootstrap.)
            System.clearProperty(SAVED_KEY_PROP);

            stubSingleWorkspace(WorkspaceRow.builder()
                .id(5L)
                .mode("PAT_ORG")
                .accountLogin("dev")
                .personalAccessToken(null)
                .build());

            new WorkspaceConnectionBackfillChange().execute(database);

            InsertCapture capture = onlyInsert();
            assertThat(capture.credentialsEncrypted).isNull();
            assertThat(capture.credentialsAlg).isNull();
        }

        @Test
        @DisplayName("Encrypted PAT but no key configured → fail-fast with a guidance message")
        void encryptedPatNoKey_failsFast() throws Exception {
            // legacyEncrypt() above needs the key — capture the ciphertext first, then
            // clear the key so the change can't find it.
            String encryptedPat = legacyEncrypt("ghp_real-token");
            System.clearProperty(SAVED_KEY_PROP);

            stubSingleWorkspace(WorkspaceRow.builder()
                .id(5L)
                .mode("PAT_ORG")
                .accountLogin("dev")
                .personalAccessToken(encryptedPat)
                .build());

            assertThatThrownBy(() -> new WorkspaceConnectionBackfillChange().execute(database))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("hephaestus.security.encryption-key");
        }
    }

    // ── Workspace ResultSet stubbing ───────────────────────────────────────

    /**
     * Stubs the workspace result-set to return exactly one row built from {@code row},
     * then end-of-cursor. Each call to {@code getLong(column)} returns the stubbed value
     * and the next call to {@code wasNull()} returns whether that column was null.
     */
    private void stubSingleWorkspace(WorkspaceRow row) throws Exception {
        // Per-test fresh mock ResultSet so wasNull state-tracking works cleanly.
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(selectStatement.executeQuery(anyString())).thenReturn(rs);

        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("id")).thenReturn(row.id);
        when(rs.getString("git_provider_mode")).thenReturn(row.mode);
        when(rs.getString("account_login")).thenReturn(row.accountLogin);
        when(rs.getString("server_url")).thenReturn(row.serverUrl);
        when(rs.getString("personal_access_token")).thenReturn(row.personalAccessToken);
        when(rs.getString("slack_token")).thenReturn(row.slackToken);
        when(rs.getString("leaderboard_notification_team")).thenReturn(row.leaderboardNotificationTeam);
        when(rs.getString("leaderboard_notification_channel_id"))
            .thenReturn(row.leaderboardNotificationChannelId);

        // Track which long column was just read so the immediately-following wasNull()
        // returns the correct flag. The production code calls getLong → wasNull in
        // tight sequence inside nullableLong(), so a single-slot state field is enough.
        Map<String, Long> longValues = new LinkedHashMap<>();
        longValues.put("installation_id", row.installationId == null ? 0L : row.installationId);
        longValues.put("gitlab_group_id", row.gitlabGroupId == null ? 0L : row.gitlabGroupId);
        longValues.put("gitlab_webhook_id", row.gitlabWebhookId == null ? 0L : row.gitlabWebhookId);
        Map<String, Boolean> nullFlags = new LinkedHashMap<>();
        nullFlags.put("installation_id", row.installationId == null);
        nullFlags.put("gitlab_group_id", row.gitlabGroupId == null);
        nullFlags.put("gitlab_webhook_id", row.gitlabWebhookId == null);

        // Latest-column accumulator drives wasNull. Using a 1-element array because
        // a lambda can't capture a re-assigned local variable.
        String[] lastLongColumn = { null };
        for (Map.Entry<String, Long> entry : longValues.entrySet()) {
            when(rs.getLong(entry.getKey())).thenAnswer(inv -> {
                lastLongColumn[0] = entry.getKey();
                return entry.getValue();
            });
        }
        when(rs.wasNull()).thenAnswer(inv ->
            lastLongColumn[0] != null && nullFlags.getOrDefault(lastLongColumn[0], false));
    }

    // ── INSERT capture wiring ──────────────────────────────────────────────

    /**
     * Records each {@code insertStatement.setX(index, value)} into {@code nextInsert},
     * then flushes that map into {@code capturedInserts} on each {@code executeUpdate()}.
     * Lets each test inspect the exact bind parameters issued per insert.
     */
    private void wireInsertCapture() throws Exception {
        lenient().doAnswer(inv -> {
            nextInsert.put(inv.getArgument(0, Integer.class), inv.getArgument(1));
            return null;
        }).when(insertStatement).setLong(anyInt(), anyLong());
        lenient().doAnswer(inv -> {
            nextInsert.put(inv.getArgument(0, Integer.class), inv.getArgument(1));
            return null;
        }).when(insertStatement).setString(anyInt(), anyString());
        lenient().doAnswer(inv -> {
            nextInsert.put(inv.getArgument(0, Integer.class), inv.getArgument(1));
            return null;
        }).when(insertStatement).setBytes(anyInt(), any(byte[].class));
        lenient().doAnswer(inv -> {
            // setNull → record the slot as null so it overrides any prior default.
            nextInsert.put(inv.getArgument(0, Integer.class), null);
            return null;
        }).when(insertStatement).setNull(anyInt(), anyInt());

        lenient().when(insertStatement.executeUpdate()).thenAnswer(inv -> {
            capturedInserts.add(new InsertCapture(
                (Long)   nextInsert.get(1),
                (String) nextInsert.get(2),
                (String) nextInsert.get(3),
                (String) nextInsert.get(4),
                (String) nextInsert.get(5),
                (byte[]) nextInsert.get(6),
                (String) nextInsert.get(7)
            ));
            nextInsert.clear();
            return 1;
        });
    }

    private InsertCapture onlyInsert() {
        assertThat(capturedInserts).hasSize(1);
        return capturedInserts.get(0);
    }

    /** Encrypt a plaintext string in the legacy {@code EncryptedStringConverter} format. */
    private static String legacyEncrypt(String plaintext) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
            new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"),
            new GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ct, 0, combined, iv.length, ct.length);
        return "ENC:" + Base64.getEncoder().encodeToString(combined);
    }

    /** Recorded INSERT parameters from one execution. Column order mirrors the SQL. */
    private record InsertCapture(
        @Nullable Long workspaceId,
        @Nullable String kind,
        @Nullable String instanceKey,
        @Nullable String state,
        @Nullable String configJson,
        @Nullable byte[] credentialsEncrypted,
        @Nullable String credentialsAlg
    ) {
    }

    /** Builder-style fluent row spec to keep tests readable. */
    private static final class WorkspaceRow {
        long id;
        String mode;
        @Nullable Long installationId;
        @Nullable String accountLogin;
        @Nullable String serverUrl;
        @Nullable String personalAccessToken;
        @Nullable Long gitlabGroupId;
        @Nullable Long gitlabWebhookId;
        @Nullable String slackToken;
        @Nullable String leaderboardNotificationTeam;
        @Nullable String leaderboardNotificationChannelId;

        static Builder builder() { return new Builder(); }

        static final class Builder {
            private final WorkspaceRow r = new WorkspaceRow();
            Builder id(long v) { r.id = v; return this; }
            Builder mode(String v) { r.mode = v; return this; }
            Builder installationId(@Nullable Long v) { r.installationId = v; return this; }
            Builder accountLogin(@Nullable String v) { r.accountLogin = v; return this; }
            Builder serverUrl(@Nullable String v) { r.serverUrl = v; return this; }
            Builder personalAccessToken(@Nullable String v) { r.personalAccessToken = v; return this; }
            Builder gitlabGroupId(@Nullable Long v) { r.gitlabGroupId = v; return this; }
            Builder gitlabWebhookId(@Nullable Long v) { r.gitlabWebhookId = v; return this; }
            Builder slackToken(@Nullable String v) { r.slackToken = v; return this; }
            Builder leaderboardNotificationTeam(@Nullable String v) {
                r.leaderboardNotificationTeam = v;
                return this;
            }
            Builder leaderboardNotificationChannelId(@Nullable String v) {
                r.leaderboardNotificationChannelId = v;
                return this;
            }
            WorkspaceRow build() { return r; }
        }
    }
}
