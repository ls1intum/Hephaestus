package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Parking row for a GitHub App installation observed BEFORE any Hephaestus workspace
 * claims it.
 *
 * <p>Today's {@code WorkspaceInstallationService.createOrUpdateFromInstallation()} inlines
 * workspace creation on first webhook — that's wrong for the canonical user journey: an
 * admin clicks "Install Hephaestus App" on github.com first, returns to Hephaestus, signs
 * up / picks an org, and only THEN claims the installation. The {@code installation.created}
 * webhook arrives before any workspace exists.
 *
 * <p>This table holds the unclaimed installation metadata so the bootstrap flow has a row
 * to bind. The 30-day TTL ({@link #expiresAt}) is enforced by
 * {@link GithubInstallationCleanupJob} so we don't accrete orphaned installations forever.
 *
 * <p><b>Workspace-agnostic by design:</b> a row exists precisely because no workspace
 * has claimed the installation yet. Bound via {@link GithubInstallationBindingService},
 * which deletes this row and creates the corresponding {@code Connection}.
 */
@Entity
@Table(name = "github_installation_unbound")
@WorkspaceAgnostic("Pre-workspace bootstrap — no workspace has claimed the installation yet")
public class GithubInstallationUnbound {

    /** Natural key — GitHub's installation id. NOT generated. */
    @Id
    @Column(name = "installation_id", nullable = false, updatable = false)
    private Long installationId;

    @Column(name = "account_id")
    @Nullable
    private Long accountId;

    @Column(name = "account_login", length = 256)
    @Nullable
    private String accountLogin;

    /** {@code ORGANIZATION} or {@code USER}. */
    @Column(name = "account_type", length = 32)
    @Nullable
    private String accountType;

    @Column(name = "avatar_url", length = 512)
    @Nullable
    private String avatarUrl;

    /**
     * Raw JSON array of repositories observed on the {@code installation.created} webhook.
     * Stored as JSONB; treated as opaque string at the JPA boundary so we don't bind a
     * Jackson type at the entity layer. Callers parse on read.
     */
    @Column(name = "repositories", columnDefinition = "jsonb", nullable = false)
    private String repositories = "[]";

    /**
     * Default value comes from the DB ({@code NOW()}). We do not use {@code @CreationTimestamp}
     * because the DB default already provides a stable, single-source value — adding a
     * Hibernate-side default would create two divergent clocks on bulk insert / SQL path.
     */
    @Column(name = "observed_at", nullable = false, updatable = false, insertable = false)
    private Instant observedAt;

    /**
     * Default value comes from the DB ({@code NOW() + INTERVAL '30 days'}). Same rationale
     * as {@link #observedAt}.
     */
    @Column(name = "expires_at", nullable = false, insertable = false)
    private Instant expiresAt;

    protected GithubInstallationUnbound() {
    }

    public GithubInstallationUnbound(long installationId) {
        this.installationId = installationId;
    }


    public Long getInstallationId() { return installationId; }
    @Nullable public Long getAccountId() { return accountId; }
    @Nullable public String getAccountLogin() { return accountLogin; }
    @Nullable public String getAccountType() { return accountType; }
    @Nullable public String getAvatarUrl() { return avatarUrl; }
    public String getRepositories() { return repositories; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setAccountId(@Nullable Long accountId) { this.accountId = accountId; }
    public void setAccountLogin(@Nullable String accountLogin) { this.accountLogin = accountLogin; }
    public void setAccountType(@Nullable String accountType) { this.accountType = accountType; }
    public void setAvatarUrl(@Nullable String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setRepositories(String repositories) { this.repositories = repositories; }
}
