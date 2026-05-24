package de.tum.cit.aet.hephaestus.integration.registry.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig.GitHubAppConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig.GitHubPatConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig.GitLabConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig.SlackConfig;
import de.tum.cit.aet.hephaestus.integration.registry.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Base64;
import java.util.Collections;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * Liquibase {@link CustomTaskChange} that lifts the legacy per-workspace integration
 * columns into the new {@code connection} aggregate.
 *
 * <p><b>What it does</b>. For each {@code workspace} row, this change inserts up to two
 * {@code connection} rows — one for the active git provider (GITHUB or GITLAB) and one
 * for Slack if a Slack bot token is present. The shape of the inserted row is governed
 * by {@code workspace.git_provider_mode}:
 *
 * <ul>
 *   <li>{@code GITHUB_APP_INSTALLATION} → {@code kind=GITHUB}, {@code instance_key=installation_id},
 *       {@link GitHubAppConfig} JSONB, <b>no</b> credential blob (the App mints installation
 *       tokens on demand via {@code GithubTokenRefresher}).</li>
 *   <li>{@code PAT_ORG} → {@code kind=GITHUB}, {@code instance_key='pat'},
 *       {@link GitHubPatConfig} JSONB. PAT is re-encrypted from
 *       {@code EncryptedStringConverter} format into a {@code BearerToken}
 *       {@link CredentialBundle} via {@link CredentialBundleConverter}.</li>
 *   <li>{@code GITLAB_PAT} → {@code kind=GITLAB}, {@code instance_key='<serverUrl>:<groupId>'},
 *       {@link GitLabConfig} JSONB with {@code signingMode=PLAINTEXT}. PAT re-encrypted as
 *       above.</li>
 * </ul>
 *
 * <p>The Slack row is inserted independently iff {@code workspace.slack_token IS NOT NULL}.
 * The signing secret is intentionally <b>not</b> migrated to the per-Connection bundle —
 * it stays in {@code hephaestus.slack.signing-secret} as an app-global property, matching
 * the way Slack manifests share signing secrets across all installs of one app.
 *
 * <p><b>Idempotency</b>. Re-running is safe: each insert is preceded by a
 * {@code SELECT 1 FROM connection WHERE workspace_id=? AND kind=?} guard. The first run
 * inserts, subsequent runs skip with a debug log line.
 *
 * <p><b>Encryption key resolution</b>. The change reads the encryption key from
 * the same two sources {@code EncryptedStringConverter} reads from — the
 * {@code hephaestus.security.encryption-key} system property (Spring binds Maven/JVM
 * properties there) or the {@code HEPHAESTUS_ENCRYPTION_KEY} environment variable
 * (Spring's relaxed binding fallback). If neither is set, the change throws — silently
 * skipping credential rewrap would leave PAT-mode workspaces unable to authenticate
 * after the column drop. If a workspace HAS no credential to migrate (App mode, or
 * a misconfigured row with NULL PAT) the change proceeds without needing the key.
 *
 * <p>This change does <b>not</b> drop the legacy columns — the DROP is a separate
 * changeset in the same file (guarded by {@code context="connection-cutover"}) so the
 * backfill can be inspected (and re-applied idempotently) before columns disappear.
 */
public class WorkspaceConnectionBackfillChange implements CustomTaskChange {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceConnectionBackfillChange.class);

    /** Mirrors {@link CredentialBundleConverter#ALGORITHM_TAG} but kept as a literal so the
     *  changeset doesn't need to drag the converter onto Liquibase's classpath at apply time. */
    private static final String ALGORITHM_TAG = "aesgcm-v1";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String LEGACY_PREFIX = "ENC:";

    private static final SecureRandom IV_GENERATOR = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ResourceAccessor resourceAccessor;


    @Override
    public String getConfirmationMessage() {
        return "Backfilled connection rows from legacy workspace integration columns";
    }

    @Override
    public void setUp() throws SetupException {
        // No setup required.
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }


    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection conn = (JdbcConnection) database.getConnection();
        // Lazy: only build the key if we actually find encrypted credentials to migrate.
        // Workspaces with only App-mode installations don't carry PATs, and a misconfigured
        // local dev env (no key) shouldn't block App-mode backfill.
        EncryptionContext crypto = new EncryptionContext();

        int gitInserted = 0;
        int slackInserted = 0;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, git_provider_mode, installation_id, account_login, server_url, "
                     + "personal_access_token, gitlab_group_id, gitlab_webhook_id, "
                     + "slack_token, leaderboard_notification_team, leaderboard_notification_channel_id "
                     + "FROM workspace")) {

            while (rs.next()) {
                long workspaceId = rs.getLong("id");
                String mode = rs.getString("git_provider_mode");
                String accountLogin = rs.getString("account_login");
                String serverUrl = rs.getString("server_url");
                Long installationId = nullableLong(rs, "installation_id");
                String pat = rs.getString("personal_access_token");
                Long gitlabGroupId = nullableLong(rs, "gitlab_group_id");
                Long gitlabWebhookId = nullableLong(rs, "gitlab_webhook_id");
                String slackToken = rs.getString("slack_token");
                String slackTeamLabel = rs.getString("leaderboard_notification_team");
                String slackChannelId = rs.getString("leaderboard_notification_channel_id");

                if (mode != null) {
                    gitInserted += backfillGit(
                        conn, workspaceId, mode, installationId, accountLogin, serverUrl,
                        pat, gitlabGroupId, gitlabWebhookId, crypto);
                }

                if (slackToken != null && !slackToken.isBlank()) {
                    slackInserted += backfillSlack(
                        conn, workspaceId, slackToken, slackTeamLabel, slackChannelId, crypto);
                }
            }
        } catch (Exception e) {
            throw new CustomChangeException("WorkspaceConnectionBackfillChange failed", e);
        }

        log.info(
            "WorkspaceConnectionBackfillChange complete: git_inserted={}, slack_inserted={}",
            gitInserted, slackInserted);
    }


    /** Returns 1 if a row was newly inserted, 0 if the row already existed (ON CONFLICT) or input was invalid. */
    private int backfillGit(
        JdbcConnection conn,
        long workspaceId,
        String mode,
        @Nullable Long installationId,
        @Nullable String accountLogin,
        @Nullable String serverUrl,
        @Nullable String encryptedPat,
        @Nullable Long gitlabGroupId,
        @Nullable Long gitlabWebhookId,
        EncryptionContext crypto
    ) throws Exception {
        IntegrationKind kind;
        String instanceKey;
        ConnectionConfig config;
        byte[] credentialBlob;

        switch (mode) {
            case "GITHUB_APP_INSTALLATION" -> {
                if (installationId == null) {
                    log.warn("Skipping workspace {}: mode=GITHUB_APP_INSTALLATION but installation_id is NULL",
                        workspaceId);
                    return 0;
                }
                kind = IntegrationKind.GITHUB;
                instanceKey = Long.toString(installationId);
                config = new GitHubAppConfig(installationId, accountLogin, serverUrl, Collections.emptySet());
                // App-mode never persists a bearer token; GithubTokenRefresher mints
                // installation tokens on demand at request time.
                credentialBlob = null;
            }
            case "PAT_ORG" -> {
                kind = IntegrationKind.GITHUB;
                instanceKey = "pat";
                config = new GitHubPatConfig(accountLogin, serverUrl, Collections.emptySet());
                credentialBlob = rewrapPat(encryptedPat, crypto);
            }
            case "GITLAB_PAT" -> {
                kind = IntegrationKind.GITLAB;
                // Compose a deterministic instance_key so multi-group support (future) maps
                // cleanly: same (workspace, kind, instance_key) ⇒ the same Connection.
                String url = serverUrl != null ? serverUrl : "https://gitlab.com";
                instanceKey = url + ":" + (gitlabGroupId != null ? gitlabGroupId : "");
                config = new GitLabConfig(
                    url,
                    gitlabGroupId,
                    gitlabWebhookId,
                    GitLabConfig.SigningMode.PLAINTEXT,
                    Collections.emptySet());
                credentialBlob = rewrapPat(encryptedPat, crypto);
            }
            default -> {
                log.warn("Skipping workspace {}: unknown git_provider_mode={}", workspaceId, mode);
                return 0;
            }
        }

        int rows = insertConnection(conn, workspaceId, kind, instanceKey, config, credentialBlob);
        if (rows > 0) {
            log.info("Backfilled connection: workspace_id={}, kind={}, instance_key={}, has_credentials={}",
                workspaceId, kind, instanceKey, credentialBlob != null);
        } else {
            log.debug("Workspace {} already has a {} connection at instance_key={}; skipped by ON CONFLICT",
                workspaceId, kind, instanceKey);
        }
        return rows;
    }

    private int backfillSlack(
        JdbcConnection conn,
        long workspaceId,
        String encryptedSlackToken,
        @Nullable String teamLabel,
        @Nullable String channelId,
        EncryptionContext crypto
    ) throws Exception {
        SlackConfig config = new SlackConfig(null, null, channelId, teamLabel, Collections.emptySet());
        byte[] credentialBlob = rewrapPat(encryptedSlackToken, crypto);
        // Sentinel instance_key — Postgres treats NULL as distinct in UNIQUE indexes
        // (pre-15 behavior, and we don't yet enforce NULLS NOT DISTINCT), so a real
        // NULL would let two rows race in at once. The first successful Slack call
        // after migration replaces this with the real team id via SlackTokenService.
        int rows = insertConnection(
            conn, workspaceId, IntegrationKind.SLACK, "pending-team-bind", config, credentialBlob);
        if (rows > 0) {
            log.info("Backfilled SLACK connection: workspace_id={}, channel_id={}, has_credentials={}",
                workspaceId, channelId, credentialBlob != null);
        } else {
            log.debug("Workspace {} already has a SLACK connection; skipped by ON CONFLICT", workspaceId);
        }
        return rows;
    }


    /**
     * Atomically idempotent insert via {@code ON CONFLICT (workspace_id, kind, instance_key) DO NOTHING}
     * against the {@code uq_connection} unique constraint. Returns {@code 1} when a new row was
     * inserted, {@code 0} when the row already existed — concurrent Liquibase apply (multi-node
     * boot) cannot produce duplicates or fail this changeset.
     */
    private int insertConnection(
        JdbcConnection conn,
        long workspaceId,
        IntegrationKind kind,
        @Nullable String instanceKey,
        ConnectionConfig config,
        @Nullable byte[] credentialBlob
    ) throws Exception {
        String configJson = MAPPER.writeValueAsString(config);
        // Cast config to jsonb in SQL — the column type forces this; otherwise PG complains
        // about parameter type 'character varying' on a 'jsonb' column.
        String sql = "INSERT INTO connection ("
            + "workspace_id, kind, instance_key, state, config, "
            + "credentials_encrypted, credentials_alg, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, NOW(), NOW(), 0) "
            + "ON CONFLICT (workspace_id, kind, instance_key) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ps.setString(2, kind.name());
            if (instanceKey == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, instanceKey);
            }
            ps.setString(4, IntegrationState.ACTIVE.name());
            ps.setString(5, configJson);
            if (credentialBlob == null) {
                ps.setNull(6, Types.BINARY);
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setBytes(6, credentialBlob);
                ps.setString(7, ALGORITHM_TAG);
            }
            return ps.executeUpdate();
        }
    }

    @Nullable
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }


    /**
     * Decrypts a value written by {@link de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter}
     * and re-encrypts it as a {@link BearerToken} {@link CredentialBundle} in the
     * {@link CredentialBundleConverter} format. Returns {@code null} if the input is
     * blank.
     */
    @Nullable
    private static byte[] rewrapPat(@Nullable String encryptedValue, EncryptionContext crypto) throws Exception {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        String plaintext = decryptLegacy(encryptedValue, crypto.requireKey());
        BearerToken bundle = new BearerToken(plaintext, null);
        byte[] json = MAPPER.writeValueAsBytes(bundle);
        return encryptBundle(json, crypto.requireKey());
    }

    private static String decryptLegacy(String dbValue, byte[] keyBytes) throws Exception {
        if (!dbValue.startsWith(LEGACY_PREFIX)) {
            // EncryptedStringConverter passes unencrypted legacy data through unchanged.
            // Preserve that behaviour during migration.
            return dbValue;
        }
        byte[] combined = Base64.getDecoder().decode(dbValue.substring(LEGACY_PREFIX.length()));
        if (combined.length < GCM_IV_LENGTH + 1) {
            throw new IllegalStateException("Legacy ciphertext too short: " + combined.length);
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
            new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private static byte[] encryptBundle(byte[] plaintext, byte[] keyBytes) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        IV_GENERATOR.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
            new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plaintext);

        byte[] combined = new byte[GCM_IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
        System.arraycopy(cipherText, 0, combined, GCM_IV_LENGTH, cipherText.length);
        return combined;
    }

    /**
     * Lazy holder for the encryption key. Resolves once on first use so a workspace
     * inventory with zero PATs never has to read the key — useful for production
     * environments where the key is provided only via the running app and Liquibase
     * is run with a stripped environment.
     */
    private static final class EncryptionContext {
        @Nullable private byte[] cachedKey;
        private boolean resolved;

        byte[] requireKey() {
            if (!resolved) {
                cachedKey = resolveKey();
                resolved = true;
            }
            if (cachedKey == null) {
                throw new IllegalStateException(
                    "WorkspaceConnectionBackfillChange found an encrypted credential to rewrap, "
                        + "but hephaestus.security.encryption-key / HEPHAESTUS_ENCRYPTION_KEY is not set. "
                        + "Re-run Liquibase with the same key the running application uses.");
            }
            return cachedKey;
        }

        @Nullable
        private static byte[] resolveKey() {
            String key = System.getProperty("hephaestus.security.encryption-key");
            if (key == null || key.isBlank()) {
                key = System.getenv("HEPHAESTUS_ENCRYPTION_KEY");
            }
            if (key == null || key.isBlank()) {
                return null;
            }
            if (key.length() != 32) {
                throw new IllegalArgumentException(
                    "Encryption key must be exactly 32 characters (256 bits). Got: " + key.length());
            }
            return key.getBytes(StandardCharsets.UTF_8);
        }
    }

}
