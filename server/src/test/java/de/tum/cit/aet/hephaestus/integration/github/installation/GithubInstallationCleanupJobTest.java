package de.tum.cit.aet.hephaestus.integration.github.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Unit tests for {@link GithubInstallationCleanupJob}.
 *
 * <p>The cleanup job is the local + GitHub-side coordinator. Tests exercise:
 * <ul>
 *   <li>Per-row GitHub-DELETE → local-DELETE ordering (GitHub success required first).</li>
 *   <li>A failing GitHub-DELETE leaves the local row intact so the next pass retries.</li>
 *   <li>The {@code delete-from-github=false} flag skips the GitHub call entirely.</li>
 *   <li>One failing row does not abort the batch.</li>
 *   <li>{@code @ConditionalOnProperty} + {@code @SchedulerLock} annotations are present
 *       on the load-bearing surface.</li>
 * </ul>
 */
@DisplayName("GithubInstallationCleanupJob — unit")
class GithubInstallationCleanupJobTest extends BaseUnitTest {

    @Mock
    private GithubInstallationUnboundRepository repository;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    private SimpleMeterRegistry meterRegistry;
    private GithubInstallationCleanupJob job;

    private GithubInstallationCleanupJob build(boolean deleteFromGithub) {
        this.meterRegistry = new SimpleMeterRegistry();
        return new GithubInstallationCleanupJob(repository, gitHubAppTokenService, deleteFromGithub, meterRegistry);
    }

    @Test
    @DisplayName("processes all expired rows: GitHub DELETE then local delete; counter increments per success")
    void processesAllExpiredRows() {
        job = build(true);
        List<GithubInstallationUnbound> expired = List.of(row(1L), row(2L), row(3L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(gitHubAppTokenService).deleteInstallation(anyLong());
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));

        job.cleanupExpired();

        verify(gitHubAppTokenService).deleteInstallation(1L);
        verify(gitHubAppTokenService).deleteInstallation(2L);
        verify(gitHubAppTokenService).deleteInstallation(3L);
        verify(repository, times(3)).delete(any(GithubInstallationUnbound.class));
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(3.0);
        assertThat(counter("github.installation.unbound.github_delete_failed").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("delete-from-github=false: skips GitHub call, still cleans local rows")
    void skipsGithubWhenFlagOff() {
        job = build(false);
        List<GithubInstallationUnbound> expired = List.of(row(1L), row(2L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));

        job.cleanupExpired();

        verify(gitHubAppTokenService, never()).deleteInstallation(anyLong());
        verify(repository, times(2)).delete(any(GithubInstallationUnbound.class));
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("GitHub DELETE failure for one row: local row stays, next-pass retries; batch continues")
    void githubDeleteFailureKeepsLocalRow() {
        job = build(true);
        GithubInstallationUnbound poison = row(2L);
        List<GithubInstallationUnbound> expired = List.of(row(1L), poison, row(3L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(gitHubAppTokenService).deleteInstallation(anyLong());
        doThrow(new RuntimeException("simulated GitHub API failure"))
            .when(gitHubAppTokenService).deleteInstallation(2L);
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));

        job.cleanupExpired();

        // GitHub DELETE was attempted for all three.
        verify(gitHubAppTokenService).deleteInstallation(1L);
        verify(gitHubAppTokenService).deleteInstallation(2L);
        verify(gitHubAppTokenService).deleteInstallation(3L);
        // But the local row for installation 2 must NOT have been deleted (would orphan upstream).
        verify(repository, never()).delete(poison);
        verify(repository).delete(expired.get(0));
        verify(repository).delete(expired.get(2));
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(2.0);
        assertThat(counter("github.installation.unbound.github_delete_failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("local delete failure after successful GitHub DELETE does not abort the batch")
    void localDeleteFailureDoesNotBlockBatch() {
        job = build(true);
        GithubInstallationUnbound poison = row(2L);
        List<GithubInstallationUnbound> expired = List.of(row(1L), poison, row(3L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(gitHubAppTokenService).deleteInstallation(anyLong());
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));
        doThrow(new RuntimeException("simulated DB error")).when(repository).delete(poison);

        job.cleanupExpired();

        verify(repository, times(3)).delete(any(GithubInstallationUnbound.class));
        // Two successful (counter increments) + one local-delete failure.
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("empty expired list short-circuits with no work and no GitHub calls")
    void emptyListShortCircuits() {
        job = build(true);
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(List.of());

        job.cleanupExpired();

        verify(gitHubAppTokenService, never()).deleteInstallation(anyLong());
        verify(repository, never()).delete(any(GithubInstallationUnbound.class));
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("disabled-by-default: @ConditionalOnProperty gates bean activation")
    void conditionalOnPropertyPresent() {
        ConditionalOnProperty conditional = GithubInstallationCleanupJob.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(conditional).as("@ConditionalOnProperty must be present").isNotNull();
        assertThat(conditional.name())
            .as("Property name must match the documented opt-in flag")
            .containsExactly("hephaestus.integration.github.unbound-cleanup.enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("@SchedulerLock present on cleanupExpired() with bounded lockAtMostFor / lockAtLeastFor")
    void schedulerLockPresent() throws NoSuchMethodException {
        SchedulerLock lock = GithubInstallationCleanupJob.class
            .getMethod("cleanupExpired")
            .getAnnotation(SchedulerLock.class);
        assertThat(lock).as("@SchedulerLock must be present").isNotNull();
        assertThat(lock.name()).isEqualTo("github-installation-unbound-cleanup");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT30M");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT1M");
    }

    @Test
    @DisplayName("BATCH_LIMIT honoured: rows beyond the cap are deferred to the next pass")
    void batchLimitHonoured() {
        job = build(true);
        // 60 rows in the queue, batch cap is 50.
        List<GithubInstallationUnbound> expired = new java.util.ArrayList<>();
        for (long i = 1; i <= 60; i++) expired.add(row(i));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doNothing().when(gitHubAppTokenService).deleteInstallation(anyLong());
        doNothing().when(repository).delete(any(GithubInstallationUnbound.class));

        job.cleanupExpired();

        verify(gitHubAppTokenService, times(GithubInstallationCleanupJob.BATCH_LIMIT)).deleteInstallation(anyLong());
        verify(repository, times(GithubInstallationCleanupJob.BATCH_LIMIT)).delete(any(GithubInstallationUnbound.class));
        assertThat(counter("github.installation.unbound.cleaned").count())
            .isEqualTo((double) GithubInstallationCleanupJob.BATCH_LIMIT);
        // Last row never touched.
        verify(gitHubAppTokenService, never()).deleteInstallation(60L);
    }

    @Test
    @DisplayName("if delete-from-github=true but service rejects all calls, no local rows are deleted")
    void totalGithubOutageStopsAllDeletes() {
        job = build(true);
        List<GithubInstallationUnbound> expired = List.of(row(1L), row(2L));
        when(repository.findByExpiresAtBefore(any(Instant.class))).thenReturn(expired);
        doThrow(new RuntimeException("GitHub API down"))
            .when(gitHubAppTokenService).deleteInstallation(anyLong());

        job.cleanupExpired();

        verify(repository, never()).delete(any(GithubInstallationUnbound.class));
        assertThat(counter("github.installation.unbound.cleaned").count()).isEqualTo(0.0);
        assertThat(counter("github.installation.unbound.github_delete_failed").count()).isEqualTo(2.0);
    }

    private Counter counter(String name) {
        Counter c = meterRegistry.find(name).counter();
        assertThat(c).as("counter %s must exist", name).isNotNull();
        return c;
    }

    private static GithubInstallationUnbound row(long id) {
        return new GithubInstallationUnbound(id);
    }
}
